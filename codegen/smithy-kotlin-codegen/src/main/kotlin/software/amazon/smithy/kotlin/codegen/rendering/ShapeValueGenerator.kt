/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.SymbolProperty
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.model.isEnum
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.StreamingTrait
import kotlin.math.round

/**
 * Generates a shape type declaration based on the parameters provided.
 */
class ShapeValueGenerator(
    internal val model: Model,
    internal val symbolProvider: SymbolProvider,
) {

    /**
     * Renders the generation of a shape value declaration for the given parameters inline
     * with the current writer.
     *
     * @param writer writer to write generated code with.
     * @param shape the shape that will be declared.
     * @param params parameters to fill the generated shape declaration.
     */
    fun instantiateShapeInline(writer: KotlinWriter, shape: Shape, params: Node) {
        if (shape.isStructureShape) {
            classDeclaration(writer, shape.asStructureShape().get()) {
                writeShapeValuesInline(writer, shape, params)
            }
        } else {
            writeShapeValuesInline(writer, shape, params)
        }
    }

    /**
     * Renders the mapping of the shape fields to the given parameters
     *
     * @param writer writer to write generated code with.
     * @param shape the shape that will be declared.
     * @param params parameters to fill the generated shape declaration.
     */
    fun writeShapeValues(writer: KotlinWriter, shape: Shape, params: Node) {
        writer.ensureNewline()
        writeShapeValuesInline(writer, shape, params)
        writer.ensureNewline()
    }

    private fun writeShapeValuesInline(writer: KotlinWriter, shape: Shape, params: Node) {
        val nodeVisitor = ShapeValueNodeVisitor(writer, this, shape)
        when (shape.type) {
            ShapeType.STRUCTURE -> params.accept(nodeVisitor)
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
            .ensureNewline()
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
            .ensureNewline()
            .write(")")

        writer.popState()
    }

    private fun collectionDeclaration(writer: KotlinWriter, shape: CollectionShape, block: () -> Unit) {
        writer.pushState()
        writer.trimTrailingSpaces(false)

        val collectionSymbol = symbolProvider.toSymbol(shape)
        val generatorFn = collectionSymbol.expectProperty(SymbolProperty.IMMUTABLE_COLLECTION_FUNCTION)

        collectionSymbol.references.forEach {
            writer.addImport(it.symbol)
        }
        writer.writeInline("$generatorFn(\n")
            .indent()
            .call { block() }
            .dedent()
            .ensureNewline()
            .write(")")

        writer.popState()
    }

    private fun primitiveDeclaration(writer: KotlinWriter, shape: Shape, block: () -> Unit) {
        val suffix = when {
            shape.isEnum -> {
                val symbol = symbolProvider.toSymbol(shape)
                writer.writeInline("#L.fromValue(", symbol.name)
                ")"
            }

            shape.type == ShapeType.BLOB -> {
                if (shape.hasTrait<StreamingTrait>()) {
                    writer.writeInline("#T.fromString(", RuntimeTypes.Core.Content.ByteStream)
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
        val currShape: Shape,
    ) : NodeVisitor<Unit> {

        override fun objectNode(node: ObjectNode) {
            if (currShape.type == ShapeType.DOCUMENT) {
                writer
                    .writeInline("#T {\n", RuntimeTypes.Core.Content.buildDocument)
                    .indent()
            }

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
                        generator.instantiateShapeInline(writer, memberShape, valueNode)
                        if (i < node.members.size - 1) {
                            writer.ensureNewline()
                        }
                    }
                    is MapShape -> {
                        memberShape = generator.model.expectShape(currShape.value.target)
                        writer.writeInline("#S to ", keyNode.value)

                        if (valueNode is NullNode) {
                            writer.write("null")
                        } else {
                            generator.instantiateShapeInline(writer, memberShape, valueNode)
                            if (i < node.members.size - 1) {
                                writer.writeInline(",\n")
                            }
                        }
                    }
                    is DocumentShape -> {
                        writer.writeInline("#S to ", keyNode.value)
                        generator.instantiateShapeInline(writer, currShape, valueNode)
                        writer.writeInline("\n")
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
                        generator.instantiateShapeInline(writer, memberShape, valueNode)
                        writer.writeInline(")")
                    }
                    else -> throw CodegenException("unexpected shape type " + currShape.type)
                }
                i++
            }

            if (currShape.type === ShapeType.DOCUMENT) {
                writer
                    .dedent()
                    .writeInline("}")
            }
        }

        override fun stringNode(node: StringNode) {
            when (currShape.type) {
                ShapeType.DOUBLE,
                ShapeType.FLOAT,
                -> {
                    val symbolName = generator.symbolProvider.toSymbol(currShape).name
                    val symbolMember = when (node.value) {
                        "Infinity" -> "POSITIVE_INFINITY"
                        "-Infinity" -> "NEGATIVE_INFINITY"
                        "NaN" -> "NaN"
                        else -> throw CodegenException("""Cannot interpret $symbolName value "${node.value}".""")
                    }
                    writer.writeInline("#L", "$symbolName.$symbolMember")
                }

                ShapeType.BIG_INTEGER -> writer.writeInline("#T(#S)", RuntimeTypes.Core.Content.BigInteger, node.value)
                ShapeType.BIG_DECIMAL -> writer.writeInline("#T(#S)", RuntimeTypes.Core.Content.BigDecimal, node.value)

                ShapeType.DOCUMENT -> writer.writeInline("#T(#S)", RuntimeTypes.Core.Content.Document, node.value)

                else -> writer.writeInline("#S", node.value)
            }
        }

        override fun nullNode(node: NullNode) {
            writer.writeInline("null")
        }

        override fun arrayNode(node: ArrayNode) {
            when (currShape.type) {
                ShapeType.DOCUMENT -> {
                    writer.withInlineBlock("#T(", ")", RuntimeTypes.Core.Content.Document) {
                        writer.withInlineBlock("listOf(", ")") {
                            node.elements.forEach {
                                generator.instantiateShapeInline(writer, currShape, it)
                                writer.unwrite(writer.newline)
                                writer.writeInline(",\n")
                            }
                        }
                    }
                }

                else -> {
                    val memberShape = generator.model.expectShape((currShape as CollectionShape).member.target)
                    var i = 0
                    node.elements.forEach { element ->
                        generator.instantiateShapeInline(writer, memberShape, element)
                        writer.unwrite(writer.newline)
                        if (i < node.elements.size - 1) {
                            writer.pushState()
                            writer.indentText = ""
                            writer.writeInlineWithNoFormatting(",")
                            writer.ensureNewline()
                            writer.popState()
                        }
                        i++
                    }
                }
            }
        }

        override fun numberNode(node: NumberNode) {
            when (currShape.type) {
                ShapeType.TIMESTAMP -> {
                    writer.addImport("${KotlinDependency.CORE.namespace}.time", "Instant")

                    // the value is in seconds and CAN be fractional
                    if (node.isFloatingPointNumber) {
                        val value = node.value as Double
                        val ms = round(value * 1e3).toLong()
                        writer.writeInline("Instant.#T(#L)", RuntimeTypes.Core.fromEpochMilliseconds, ms)
                    } else {
                        writer.writeInline("Instant.fromEpochSeconds(#L, 0)", node.value)
                    }
                }

                ShapeType.BYTE, ShapeType.SHORT, ShapeType.INTEGER,
                ShapeType.LONG, ShapeType.INT_ENUM,
                -> writer.writeInline("#L", node.value)

                // ensure float/doubles that are represented as integers in the params get converted
                // since Kotlin doesn't support implicit conversions (e.g. '1' cannot be implicitly converted
                // to a Kotlin float/double)
                ShapeType.FLOAT -> writer.writeInline("#L.toFloat()", node.value)
                ShapeType.DOUBLE -> writer.writeInline("#L.toDouble()", node.value)

                ShapeType.BIG_INTEGER ->
                    writer.writeInline("#T(#S)", RuntimeTypes.Core.Content.BigInteger, node.value.toString())

                ShapeType.BIG_DECIMAL ->
                    writer.writeInline("#T(#S)", RuntimeTypes.Core.Content.BigDecimal, node.value.toString())

                ShapeType.DOCUMENT -> writer.writeInline(
                    "#T(#L#L)",
                    RuntimeTypes.Core.Content.Document,
                    node.value,
                    if (node.isFloatingPointNumber) "F" else "L",
                )

                else -> throw CodegenException("unexpected shape type $currShape for numberNode")
            }
        }

        override fun booleanNode(node: BooleanNode) {
            when (currShape.type) {
                ShapeType.DOCUMENT -> writer.writeInline("#T(#L)", RuntimeTypes.Core.Content.Document, node.value)
                ShapeType.BOOLEAN -> writer.writeInline("#L", if (node.value) "true" else "false")
                else -> throw CodegenException("unexpected shape type $currShape for boolean value")
            }
        }
    }
}
