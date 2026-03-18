/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.*
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.utils.toPascalCase
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.node.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.Trait

private val runtimeTraitsIds = setOf(
    "aws.protocols#awsQueryError",
    "smithy.api#default",
    "smithy.api#endpoint",
    "smithy.api#enum",
    "smithy.api#eventHeader",
    "smithy.api#eventPayload",
    "smithy.api#hostLabel",
    "smithy.api#http",
    "smithy.api#httpError",
    "smithy.api#httpHeader",
    "smithy.api#httpLabel",
    "smithy.api#httpPayload",
    "smithy.api#httpPrefixHeaders",
    "smithy.api#httpQuery",
    "smithy.api#httpQueryParams",
    "smithy.api#httpResponseCode",
    "smithy.api#idempotencyToken",
    "smithy.api#jsonName",
    "smithy.api#length",
    "smithy.api#mediaType",
    "smithy.api#required",
    "smithy.api#requiresLength",
    "smithy.api#sensitive",
    "smithy.api#sparse",
    "smithy.api#streaming",
    "smithy.api#timestampFormat",
    "smithy.api#uniqueItems",
    "smithy.api#xmlAttributes",
    "smithy.api#xmlFlattened",
    "smithy.api#xmlName",
    "smithy.api#xmlNamespace",
    "smithy.rules#contextParam",
)

private val SerdeTypes = RuntimeTypes.Serde

class SchemaGenerator(val ctx: RenderingContext<ServiceShape>) {
    private val service = requireNotNull(ctx.shape)
    private val writer = ctx.writer

    fun render() {
        writer.apply {
            service.orderedDependencies().filterNot { it is MemberShape }.forEach { shape ->
                writeDeclaration(shape)
            }
        }
    }

    private fun KotlinWriter.writeDeclaration(shape: Shape) {
        write("")
        writeInline("#L val #L: #T = ", ctx.settings.api.visibility, shape.schemaName, shape.schemaType)

        when (shape) {
            is SimpleShape -> writeScalarInline(shape)
            is MemberShape -> writeMemberInline(shape)

            is StructureShape,
            is UnionShape,
            -> writeStructInline(shape)

            is ListShape -> writeListInline(shape)
            is MapShape -> writeMapInline(shape)
            is OperationShape -> writeOperationInline(shape)
            is ServiceShape -> writeServiceInline(shape)

            else -> error("Unhandled shape type: $shape")
        }
        ensureNewline()
    }

    private fun KotlinWriter.writeSchemaInline(
        shape: Shape,
        block: KotlinWriter.() -> Unit,
    ) {
        withInlineBlock("#T(", ")", shape.schemaType) {
            write("#T(#S),", SerdeTypes.ShapeId, shape.id.toString())
            write("#T.#L,", SerdeTypes.ShapeType, shape.type.translateName)
            block()
            writeTraits(shape.allTraits.values)
        }
    }

    private fun KotlinWriter.writeListInline(shape: ListShape) = writeSchemaInline(shape) {
        writeInline("member = ")
        writeMemberInList(shape.member)
    }

    private fun KotlinWriter.writeMapInline(shape: MapShape) = writeSchemaInline(shape) {
        writeInline("key = ")
        writeMemberInList(shape.key)

        writeInline("value = ")
        writeMemberInList(shape.value)
    }

    private fun KotlinWriter.writeMemberInline(shape: MemberShape) = writeSchemaInline(shape) {
        write("target = lazy { #L },", shape.target.asShape.schemaName)
    }

    private fun KotlinWriter.writeMemberInList(shape: MemberShape) {
        writeMemberInline(shape)
        write(",")
    }

    private fun KotlinWriter.writeOperationInline(shape: OperationShape) = writeSchemaInline(shape) {
        write("input = #L,", shape.inputShape.asShape.schemaName)
        write("output = #L,", shape.outputShape.asShape.schemaName)
    }

    private fun KotlinWriter.writeScalarInline(shape: SimpleShape) = writeSchemaInline(shape) {
        // ??
    }

    private fun KotlinWriter.writeServiceInline(shape: ServiceShape) {
        writeSchemaInline(shape) {
            writeInline("operations = ")
            withListOf(shape.operations) {
                write("#L,", it.asShape.schemaName)
            }

            writeInline("errors = ")
            withListOf(shape.errorsSet) {
                write("#L,", it.asShape.schemaName)
            }
        }
    }

    private fun KotlinWriter.writeStructInline(shape: Shape) = writeSchemaInline(shape) {
        writeInline("members = ")
        withListOf(shape.members()) {
            writeMemberInList(it)
        }
    }

    private fun KotlinWriter.writeTraits(traits: Collection<Trait>) {
        val eligibleTraits = traits.filter { it.toShapeId().toString() in runtimeTraitsIds }
        writeInline("traits = ")
        withListOf(eligibleTraits) { trait ->
            val traitShape = trait.toShapeId().asShape
            withBlock("#T(", "),", SerdeTypes.Trait) {
                write("#T(#S),", SerdeTypes.ShapeId, trait.toShapeId().toString())
                writeTraitNodeInline(trait.toNode())
                write(",")
                writeTraits(traitShape.allTraits.values)
            }
        }
    }

    private fun KotlinWriter.writeTraitNodeInline(node: Node) {
        when (node) {
            is ArrayNode -> {
                withInlineBlock("#T(", ")", RuntimeTypes.Core.Content.Document) {
                    node.elements.forEach { element ->
                        writeTraitNodeInlineInDocBuilder(element)
                        write(",")
                    }
                }
            }

            is BooleanNode -> writeInline("#T(#L)", RuntimeTypes.Core.Content.Document, node.value.toString())
            is NullNode -> writeInline("null")
            is ObjectNode if node.members.isEmpty() -> writeInline("null")
            is NumberNode -> writeInline("#T(#L)", RuntimeTypes.Core.Content.Document, node.value.toString())
            is ObjectNode -> writeTraitNodeInlineInDocBuilder(node)
            is StringNode -> writeInline("#T.String(#S)", RuntimeTypes.Core.Content.Document, node.value)
        }
    }

    private fun KotlinWriter.writeTraitNodeInlineInDocBuilder(node: Node) {
        when (node) {
            is ArrayNode -> {
                withInlineBlock("buildList {", "}", RuntimeTypes.Core.Content.Document) {
                    node.elements.forEach { element ->
                        writeInline("add(")
                        writeTraitNodeInlineInDocBuilder(element)
                        writeInline(")")
                    }
                }
            }

            is BooleanNode -> writeInline("#L", node.value.toString())
            is NullNode -> writeInline("null")
            is ObjectNode if node.members.isEmpty() -> writeInline("null")
            is NumberNode -> writeInline("#L", node.value.toString())

            is ObjectNode -> withInlineBlock("#T {", "}", RuntimeTypes.Core.Content.buildDocument) {
                node.members.forEach { (key, value) ->
                    writeInline("#S to ", key.value)
                    writeTraitNodeInlineInDocBuilder(value)
                    write("")
                }
            }

            is StringNode -> writeInline("#S", node.value)
        }
    }

    private fun <T> KotlinWriter.withListOf(collection: Collection<T>, block: KotlinWriter.(T) -> Unit) {
        if (collection.isEmpty()) {
            write("listOf(),")
        } else {
            withBlock("listOf(", "),") {
                collection.forEach { item -> block(item) }
            }
        }
    }

    private val Shape.schemaName: String
        get() = schemaName(this, service)

    private val Shape.schemaType: Symbol
        get() = when (this) {
            is SimpleShape -> SerdeTypes.ScalarSchema
            is MemberShape -> SerdeTypes.MemberSchema

            is StructureShape,
            is UnionShape,
            -> SerdeTypes.StructureSchema

            is ListShape -> SerdeTypes.ListSchema
            is MapShape -> SerdeTypes.MapSchema
            is OperationShape -> SerdeTypes.OperationSchema
            is ServiceShape -> SerdeTypes.ServiceSchema

            else -> error("Unrecognized shape: $this")
        }

    private val ShapeId.asShape: Shape
        get() = ctx.model.expectShape(this)

    private fun Shape.orderedDependencies(): Set<Shape> {
        val seen = mutableSetOf<Shape>()
        fun recurse(shapes: Set<Shape>): List<Set<Shape>> {
            val queue = mutableSetOf<Shape>()
            shapes.forEach { shape ->
                if (shape !in seen) {
                    seen += shape

                    when (shape) {
                        is MemberShape -> queue += shape.target.asShape

                        is StructureShape,
                        is UnionShape,
                        -> queue += shape.members()

                        is ListShape -> queue += shape.member

                        is MapShape -> {
                            queue += shape.key
                            queue += shape.value
                        }

                        is OperationShape -> {
                            queue += shape.inputShape.asShape
                            queue += shape.outputShape.asShape
                        }

                        is ServiceShape -> {
                            queue += shape.operations.map { it.asShape }
                            queue += shape.errorsSet.map { it.asShape }
                        }
                    }
                }
            }

            return when {
                queue.isEmpty() -> listOf()
                else -> recurse(queue) + listOf(queue)
            }
        }

        return recurse(setOf(this)).flatten().toSet()
    }
}

private fun schemaName(shape: Shape, service: ServiceShape): String {
    val baseName = shape.defaultName(service)
    val member = when (shape) {
        is MemberShape -> shape.memberName.toPascalCase()
        else -> ""
    }
    return "$baseName${member}Schema"
}

fun KotlinDelegator.useSchemaWriter(shape: Shape, block: (KotlinWriter) -> Unit) {
    val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)
    val schemaName = schemaName(shape, service)
    val filename = "$schemaName.kt"
    val namespace = "${ctx.settings.pkg.name}.schemas"
    useFileWriter(filename, namespace, block = block)
}

private val ShapeType.translateName: String
    get() = when (this) {
        ShapeType.INT_ENUM -> "INTEGER_ENUM"
        ShapeType.RESOURCE -> error("Kotlin codegen does not support Smithy resource types")
        else -> name
    }
