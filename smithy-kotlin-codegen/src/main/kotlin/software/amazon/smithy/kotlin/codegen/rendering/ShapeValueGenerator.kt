/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.SymbolProperty
import software.amazon.smithy.kotlin.codegen.model.hasTrait
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
            ShapeType.STRUCTURE -> classDeclaration(writer, shape.asStructureShape().get()) {
                params.accept(nodeVisitor)
            }
            ShapeType.MAP -> mapDeclaration(writer, shape.asMapShape().get()) {
                params.accept(nodeVisitor)
            }
            ShapeType.LIST, ShapeType.SET -> collectionDeclaration(writer, shape as CollectionShape) {
                params.accept(nodeVisitor)
            }
            else -> primitiveDeclaration(writer, shape) {
                params.accept(nodeVisitor)
            }
        }
    }

    private fun classDeclaration(writer: KotlinWriter, shape: StructureShape, block: () -> Unit) {
        val symbol = symbolProvider.toSymbol(shape)
        // invoke the generated DSL builder for the class
        writer.writeInline("#L {\n", symbol.name)
            .indent()
            .call { block() }
            .dedent()
            .write("")
            .write("}")
    }

    private fun mapDeclaration(writer: KotlinWriter, shape: MapShape, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)

        val collectionGeneratorFunction = symbolProvider.toSymbol(shape).expectProperty(SymbolProperty.IMMUTABLE_COLLECTION_FUNCTION)

        writer.writeInline("$collectionGeneratorFunction(\n")
            .indent()
            .call { block() }
            .dedent()
            .write("")
            .write(")")

        writer.popState()
    }

    private fun collectionDeclaration(writer: KotlinWriter, shape: CollectionShape, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)

        val collectionGeneratorFunction = symbolProvider.toSymbol(shape).expectProperty(SymbolProperty.IMMUTABLE_COLLECTION_FUNCTION)

        writer.writeInline("$collectionGeneratorFunction(\n")
            .indent()
            .call { block() }
            .dedent()
            .write("")
            .write(")")

        writer.popState()
    }

    private fun primitiveDeclaration(writer: KotlinWriter, shape: Shape, block: () -> Unit) {
        val blobHandlingSymbols = listOf(
            RuntimeTypes.Core.Content.ByteArrayContent,
            RuntimeTypes.Core.Content.ByteStream,
            RuntimeTypes.Core.Content.StringContent,
            RuntimeTypes.Core.Content.toByteArray
        )
        val suffix = when (shape.type) {
            ShapeType.STRING -> {
                if (shape.hasTrait<EnumTrait>()) {
                    val symbol = symbolProvider.toSymbol(shape)
                    writer.writeInline("#L.fromValue(", symbol.name)
                    ")"
                } else {
                    ""
                }
            }
            ShapeType.BLOB -> {
                if (shape.hasTrait<StreamingTrait>()) {
                    writer.addImport(blobHandlingSymbols)
                    writer.writeInline("StringContent(")
                    ")"
                } else {
                    // blob params are spit out as strings
                    ".encodeAsByteArray()"
                }
            }
            else -> ""
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
                        val member = currShape.getMember(keyNode.value).orElseThrow {
                            CodegenException("unknown member ${currShape.id}.${keyNode.value}")
                        }
                        memberShape = generator.model.expectShape(member.target)
                        val memberName = generator.symbolProvider.toMemberName(member)
                        writer.writeInline("#L = ", memberName)
                        generator.writeShapeValueInline(writer, memberShape, valueNode)
                        if (i < node.members.size - 1) {
                            writer.write("")
                        }
                    }
                    is MapShape -> {
                        memberShape = generator.model.expectShape(currShape.value.target)
                        writer.writeInline("#S to ", keyNode.value)

                        if (valueNode is NullNode) {
                            writer.write("null")
                        } else {
                            generator.writeShapeValueInline(writer, memberShape, valueNode)
                            if (i < node.members.size - 1) {
                                writer.writeInline(",\n")
                            }
                        }
                    }
                    is DocumentShape -> {
                        // TODO - deal with document shapes
                    }
                    is UnionShape -> {
                        val member = currShape.getMember(keyNode.value).orElseThrow {
                            CodegenException("unknown member ${currShape.id}.${keyNode.value}")
                        }
                        memberShape = generator.model.expectShape(member.target)
                        val currSymbol = generator.symbolProvider.toSymbol(currShape)
                        val memberName = generator.symbolProvider.toMemberName(member)
                        val variantName = memberName.replaceFirstChar { c -> c.uppercaseChar() }
                        writer.writeInline("${currSymbol.name}.$variantName(")
                        generator.writeShapeValueInline(writer, memberShape, valueNode)
                        writer.write(")")
                    }
                    else -> throw CodegenException("unexpected shape type " + currShape.type)
                }
                i++
            }
        }

        override fun stringNode(node: StringNode) {
            when (currShape.type) {
                ShapeType.DOUBLE,
                ShapeType.FLOAT -> {
                    val symbolName = generator.symbolProvider.toSymbol(currShape).name
                    val symbolMember = when (node.value) {
                        "Infinity" -> "POSITIVE_INFINITY"
                        "-Infinity" -> "NEGATIVE_INFINITY"
                        "NaN" -> "NaN"
                        else -> throw CodegenException("""Cannot interpret $symbolName value "${node.value}".""")
                    }
                    writer.writeInline("#L", "$symbolName.$symbolMember")
                }

                else -> writer.writeInline("#S", node.value)
            }
        }

        override fun nullNode(node: NullNode) {
            writer.writeInline("null")
        }

        override fun arrayNode(node: ArrayNode) {
            val memberShape = generator.model.expectShape((currShape as CollectionShape).member.target)
            var i = 0
            node.elements.forEach { element ->
                generator.writeShapeValueInline(writer, memberShape, element)
                if (i < node.elements.size - 1) {
                    writer.writeInline(",\n")
                }
                i++
            }
        }

        override fun numberNode(node: NumberNode) {
            when (currShape.type) {
                ShapeType.TIMESTAMP -> {
                    writer.addImport("${KotlinDependency.CORE.namespace}.time", "Instant")
                    writer.writeInline("Instant.fromEpochSeconds(#L, 0)", node.value)
                }

                ShapeType.BYTE, ShapeType.SHORT, ShapeType.INTEGER,
                ShapeType.LONG -> writer.writeInline("#L", node.value)

                // ensure float/doubles that are represented as integers in the params get converted
                // since Kotlin doesn't support implicit conversions (e.g. '1' cannot be implicitly converted
                // to a Kotlin float/double)
                ShapeType.FLOAT -> writer.writeInline("#L.toFloat()", node.value)
                ShapeType.DOUBLE -> writer.writeInline("#L.toDouble()", node.value)

                ShapeType.BIG_INTEGER, ShapeType.BIG_DECIMAL -> {
                    // TODO - We need to decide non-JVM only symbols to generate for these before we know how to assign values to them
                }

                ShapeType.DOCUMENT -> {
                    // TODO - deal with document shapes
                }

                else -> throw CodegenException("unexpected shape type $currShape for numberNode")
            }
        }

        override fun booleanNode(node: BooleanNode) {
            when (currShape.type) {
                ShapeType.DOCUMENT -> {
                    // TODO - deal with document shapes
                }
                ShapeType.BOOLEAN -> writer.writeInline("#L", if (node.value) "true" else "false")
                else -> throw CodegenException("unexpected shape type $currShape for boolean value")
            }
        }
    }
}
