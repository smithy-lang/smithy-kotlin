/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.traits.StreamingTrait

/**
 * Generates a shape type declaration based on the parameters provided.
 */
class ShapeValueGenerator(
    internal val model: Model,
    internal val symbolProvider: SymbolProvider
) {

    /**
     * Writes generation of a shape value type declaration for the given the parameters.
     *
     * @param writer writer to write generated code with.
     * @param shape the shape that will be declared.
     * @param params parameters to fill the generated shape declaration.
     */
    fun writeShapeValueInline(writer: KotlinWriter, shape: Shape, params: Node) {
        val nodeVisitor = ShapeValueNodeVisitor(writer, this, shape)
        when (shape.type) {
            ShapeType.STRUCTURE -> classDecl(writer, shape.asStructureShape().get()) {
                params.accept(nodeVisitor)
            }
            ShapeType.MAP -> mapDecl(writer, shape.asMapShape().get()) {
                params.accept(nodeVisitor)
            }
            ShapeType.LIST, ShapeType.SET -> collectionDecl(writer, shape as CollectionShape) {
                params.accept(nodeVisitor)
            }
            else -> primitiveDecl(writer, shape) {
                params.accept(nodeVisitor)
            }
        }
    }

    private fun classDecl(writer: KotlinWriter, shape: StructureShape, block: () -> Unit) {
        val symbol = symbolProvider.toSymbol(shape)
        // invoke the generated DSL builder for the class
        writer.writeInline("\$L {\n", symbol.name)
            .indent()
            .call { block() }
            .dedent()
            .write("")
            .write("}")
    }

    private fun mapDecl(writer: KotlinWriter, shape: MapShape, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)

        val targetKeyShape = model.expectShape(shape.key.target)
        val targetValueShape = model.expectShape(shape.value.target)
        val keySymbol = symbolProvider.toSymbol(targetKeyShape)
        val valueSymbol = symbolProvider.toSymbol(targetValueShape)

        writer.writeInline("mapOf<\$L, \$L>(\n", keySymbol.name, valueSymbol.name)
            .indent()
            .call { block() }
            .dedent()
            .write("")
            .write(")")

        writer.popState()
    }

    private fun collectionDecl(writer: KotlinWriter, shape: CollectionShape, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)

        val targetMemberShape = model.expectShape(shape.member.target)
        val memberSymbol = symbolProvider.toSymbol(targetMemberShape)
        val collectionType = if (shape.isListShape) "listOf" else "setOf"
        writer.writeInline("$collectionType<\$L>(\n", memberSymbol.name)
            .indent()
            .call { block() }
            .dedent()
            .write("")
            .write(")")

        writer.popState()
    }

    private fun primitiveDecl(writer: KotlinWriter, shape: Shape, block: () -> Unit) {
        var suffix = ""
        when (shape.type) {
            ShapeType.STRING -> {
                if (shape.hasTrait(EnumTrait::class.java)) {
                    val symbol = symbolProvider.toSymbol(shape)
                    writer.writeInline("\$L.fromValue(", symbol.name)
                    suffix = ")"
                }
            }
            ShapeType.BLOB -> {
                if (shape.hasTrait(StreamingTrait::class.java)) {
                    writer.addImport("${KotlinDependency.CLIENT_RT_CORE.namespace}.content", "*", "")
                    writer.writeInline("StringContent(")
                    suffix = ")"
                } else {
                    // blob params are spit out as strings
                    suffix = ".encodeAsByteArray()"
                }
            }
        }

        block()

        if (suffix.isNotBlank()) {
            writer.writeInline(suffix)
        }
    }

    /**
     * NodeVisitor to walk shape value declarations with node values.
     */
    private class ShapeValueNodeVisitor(
        val writer: KotlinWriter,
        val generator: ShapeValueGenerator,
        val currShape: Shape
    ) : NodeVisitor<Unit> {

        override fun objectNode(node: ObjectNode) {
            var i = 0
            node.members.forEach { (keyNode, valueNode) ->
                val memberShape: Shape
                when (currShape) {
                    is StructureShape -> {
                        val member = if (currShape.asStructureShape().get().getMember(keyNode.value).isPresent) {
                            currShape.asStructureShape().get().getMember(keyNode.value).get()
                        } else {
                            throw CodegenException(
                                "unknown member ${currShape.id}.${keyNode.value}"
                            )
                        }
                        memberShape = generator.model.expectShape(member.target)
                        val memberName = generator.symbolProvider.toMemberName(member)
                        // NOTE - `write()` appends a newline and keeps indentation,
                        // `writeInline()` doesn't keep indentation but also doesn't append a newline
                        // ...except it does insert indentation if it encounters a newline.
                        // This is our workaround for the moment to keep indentation but not insert
                        // a newline at the end.
                        writer.writeInline("\n\$L = ", memberName)
                        generator.writeShapeValueInline(writer, memberShape, valueNode)
                    }
                    is MapShape -> {
                        memberShape = generator.model.expectShape(currShape.value.target)
                        writer.writeInline("\n\$S to ", keyNode.value)
                        generator.writeShapeValueInline(writer, memberShape, valueNode)
                        if (i < node.members.size - 1) {
                            writer.writeInline(",")
                        }
                    }
                    is DocumentShape -> {
                        // TODO - deal with document shapes
                    }
                    else -> throw CodegenException("unexpected shape type " + currShape.type)
                }
                i++
            }
        }

        override fun stringNode(node: StringNode) {
            writer.writeInline("\$S", node.value)
        }

        override fun nullNode(node: NullNode) {
            writer.writeInline("null")
        }

        override fun arrayNode(node: ArrayNode) {
            val memberShape = generator.model.expectShape((currShape as CollectionShape).member.target)
            var i = 0
            node.elements.forEach { element ->
                writer.writeInline("\n")
                generator.writeShapeValueInline(writer, memberShape, element)
                if (i < node.elements.size - 1) {
                    writer.writeInline(",")
                }
                i++
            }
        }

        override fun numberNode(node: NumberNode) {
            when (currShape.type) {
                ShapeType.TIMESTAMP -> {
                    writer.addImport("${KotlinDependency.CLIENT_RT_CORE.namespace}.time", "Instant", "")
                    writer.writeInline("Instant.fromEpochSeconds(\$L, 0)", node.value)
                }

                ShapeType.BYTE, ShapeType.SHORT, ShapeType.INTEGER,
                ShapeType.LONG -> writer.writeInline("\$L", node.value)

                // ensure float/doubles that are represented as integers in the params get converted
                // since Kotlin doesn't support implicit conversions (e.g. '1' cannot be implicitly converted
                // to a Kotlin float/double)
                ShapeType.FLOAT -> writer.writeInline("\$L.toFloat()", node.value)
                ShapeType.DOUBLE -> writer.writeInline("\$L.toDouble()", node.value)

                ShapeType.BIG_INTEGER, ShapeType.BIG_DECIMAL -> {
                    // TODO - We need to decide non-JVM only symbols to generate for these before we know how to assign values to them
                }
                else -> throw CodegenException("unexpected shape type $currShape for numberNode")
            }
        }

        override fun booleanNode(node: BooleanNode) {
            if (currShape.type != ShapeType.BOOLEAN) {
                throw CodegenException("unexpected shape type $currShape for boolean value")
            }

            writer.writeInline("\$L", if (node.value) "true" else "false")
        }
    }
}
