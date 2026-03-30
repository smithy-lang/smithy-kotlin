/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.*
import aws.smithy.kotlin.codegen.model.*
import aws.smithy.kotlin.codegen.utils.toCamelCase
import aws.smithy.kotlin.codegen.utils.toPascalCase
import software.amazon.smithy.aws.traits.ServiceTrait
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.node.*
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.StreamingTrait
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

class SchemaGenerator(val ctx: RenderingContext<ServiceShape>) {
    private val service = requireNotNull(ctx.shape)
    private val writer = ctx.writer
    private val shapeMap = ShapeMap(service, ctx.model)

    fun render() {
        writer.apply {
            shapeMap.orderedDependencies.forEach { shape ->
                writeDeclaration(shape)
            }
        }
    }

    private fun KotlinWriter.writeDeclaration(shape: Shape) {
        write("")
        writeInline("#L val #L: #T", ctx.settings.api.visibility, shape.schemaName, shape.schemaType)

        val typeArgs = when (shape) {
            is ListShape -> listOf(shape.member.target.asShape)
            is MapShape -> listOf(shape.key.target.asShape, shape.value.target.asShape)
            is OperationShape -> listOf(shape.inputShape.asShape, shape.outputShape.asShape)
            is StructureShape -> listOf(shape)
            is UnionShape -> listOf(shape)
            else -> listOf()
        }

        if (typeArgs.isNotEmpty()) {
            val typeSpec = typeArgs.joinToString(", ", "<", ">") { "#T" }
            val typeValues = typeArgs.map { ctx.symbolProvider.toSymbol(it) }.toTypedArray()
            writeInline(typeSpec, *typeValues)
        }

        writeInline(" = ")

        when (shape) {
            is ListShape -> writeListInline(shape)
            is MapShape -> writeMapInline(shape)
            is MemberShape -> writeMemberInline(shape)
            is OperationShape -> writeOperationInline(shape)
            is ServiceShape -> writeServiceInline(shape)
            is SimpleShape -> writeScalarInline(shape)
            is StructureShape -> writeStructInline(shape)
            is UnionShape -> writeUnionInline(shape)
            else -> error("Unhandled shape type: $shape")
        }
        ensureNewline()
    }

    private fun KotlinWriter.writeSchemaInline(
        shape: Shape,
        block: KotlinWriter.() -> Unit = { },
    ) {
        withInlineBlock("#T(", ")", shape.schemaType) {
            writeInline("#T(#S, #S", RuntimeTypes.Serde.ShapeId, shape.id.namespace, shape.id.name)
            if (shape is MemberShape) writeInline(", #S", shape.id.member.get())
            write("),")

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
        val memberSymbol = ctx.symbolProvider.toSymbol(shape)
        val targetShape = shape.target.asShape

        writeInline("target = ")

        val recursive = shape in shapeMap.recursiveMembers
        if (recursive) writeInline("lazy { ")

        writeInlineWithNoFormatting(targetShape.schemaName)

        if (targetShape.isEnum) {
            writeInline(".#1T(#2T.Companion::fromValue, #2T::value)", RuntimeTypes.Serde.Schemas.asEnum, memberSymbol)
        }

        if (memberSymbol.isNullable && targetShape !is DocumentShape) { // DocumentSchema is already nullable
            writeInline(".#T()", RuntimeTypes.Serde.Schemas.asNullable)
        }

        if (recursive) writeInline(" }")

        write(",")
    }

    private fun KotlinWriter.writeMemberInList(shape: MemberShape) {
        writeMemberInline(shape)
        write(",")
    }

    private fun KotlinWriter.writeOperationInline(shape: OperationShape) = writeSchemaInline(shape) {
        write("input = #L,", shape.inputShape.asShape.schemaName)
        write("output = #L,", shape.outputShape.asShape.schemaName)
    }

    private fun KotlinWriter.writeScalarInline(shape: SimpleShape) = writeSchemaInline(shape)

    private fun KotlinWriter.writeServiceInline(shape: ServiceShape) {
        writeSchemaInline(shape) {
            writeInline("operations = ")
            withListOf(serviceOperations(shape, ctx.model)) {
                write("#L,", it.schemaName)
            }

            writeInline("errors = ")
            withListOf(shape.errorsSet) {
                write("#L,", it.asShape.schemaName)
            }
        }
    }

    private fun KotlinWriter.writeStructInline(shape: StructureShape) = writeSchemaInline(shape) {
        val structSymbol = ctx.symbolProvider.toSymbol(shape)

        writeInline("memberAccess = ")
        withListOf(shape.members()) { member ->
            withBlock("#T(", "),", RuntimeTypes.Serde.Schemas.StructureMemberAccess) {
                writeInline("schema = ")
                writeMemberInList(member)

                val memberName = ctx.symbolProvider.toMemberName(member)
                write("getter = #T::#L,", structSymbol, memberName)
                write(
                    "setter = #T(#T.Builder::#L)::set,",
                    RuntimeTypes.Serde.Schemas.property,
                    structSymbol,
                    memberName,
                )
            }
        }

        write("factory = #T.Companion::invoke,", structSymbol)
    }

    private fun KotlinWriter.writeUnionInline(shape: UnionShape) = writeSchemaInline(shape) {
        val unionSymbol = ctx.symbolProvider.toSymbol(shape)

        writeInline("memberAccess = ")
        withListOf(shape.members()) { member ->
            withBlock("#T(", "),", RuntimeTypes.Serde.Schemas.UnionMemberAccess) {
                writeInline("schema = ")
                writeMemberInList(member)

                val variantName = member.unionVariantName()
                write("asVariantOrNull = { it as? #T.#L },", unionSymbol, variantName)
                write("factory = #T::#L,", unionSymbol, variantName)
                write("valueGetter = #T.#L::value", unionSymbol, variantName)
                write("")
            }
        }
    }

    private fun KotlinWriter.writeTraits(traits: Collection<Trait>) {
        val eligibleTraits = traits.filter { it.toShapeId().toString() in runtimeTraitsIds }
        writeInline("traits = ")
        withListOf(eligibleTraits) { trait ->
            val traitShape = trait.toShapeId().asShape
            withBlock("#T(", "),", RuntimeTypes.Serde.Trait) {
                val shapeId = trait.toShapeId()
                write("#T(#S, #S),", RuntimeTypes.Serde.ShapeId, shapeId.namespace, shapeId.name)
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
        get() = schemaName(this, service).toCamelCase()

    private val Shape.schemaType: Symbol
        get() = when (this) {
            is BigDecimalShape -> RuntimeTypes.Serde.Schemas.BigDecimalSchema
            is BigIntegerShape -> RuntimeTypes.Serde.Schemas.BigIntegerSchema
            is BlobShape if hasTrait<StreamingTrait>() -> RuntimeTypes.Serde.Schemas.BlobSchemaStreaming
            is BlobShape -> RuntimeTypes.Serde.Schemas.BlobSchemaInline
            is BooleanShape -> RuntimeTypes.Serde.Schemas.BooleanSchema
            is ByteShape -> RuntimeTypes.Serde.Schemas.ByteSchema
            is DocumentShape -> RuntimeTypes.Serde.Schemas.DocumentSchema
            is DoubleShape -> RuntimeTypes.Serde.Schemas.DoubleSchema
            is EnumShape -> RuntimeTypes.Serde.Schemas.StringSchema
            is FloatShape -> RuntimeTypes.Serde.Schemas.FloatSchema
            is IntEnumShape -> RuntimeTypes.Serde.Schemas.IntegerSchema
            is IntegerShape -> RuntimeTypes.Serde.Schemas.IntegerSchema
            is ListShape -> RuntimeTypes.Serde.Schemas.ListSchema
            is LongShape -> RuntimeTypes.Serde.Schemas.LongSchema
            is MapShape -> RuntimeTypes.Serde.Schemas.MapSchema
            is MemberShape -> RuntimeTypes.Serde.Schemas.MemberSchema
            is OperationShape -> RuntimeTypes.Serde.Schemas.OperationSchema
            is ServiceShape -> RuntimeTypes.Serde.Schemas.ServiceSchema
            is ShortShape -> RuntimeTypes.Serde.Schemas.ShortSchema
            is StringShape -> RuntimeTypes.Serde.Schemas.StringSchema
            is StructureShape -> RuntimeTypes.Serde.Schemas.StructureSchema
            is TimestampShape -> RuntimeTypes.Serde.Schemas.TimestampSchema
            is UnionShape -> RuntimeTypes.Serde.Schemas.UnionSchema
            else -> error("Unrecognized shape type: $this, (ShapeType.$type)")
        }

    private val ShapeId.asShape: Shape
        get() = ctx.model.expectShape(this)
}

private fun schemaName(shape: Shape, service: ServiceShape): String {
    val baseName = when (shape) {
        is ServiceShape -> clientName(shape.expectTrait<ServiceTrait>().sdkId)
        else -> shape.defaultName(service)
    }

    val member = when (shape) {
        is MemberShape -> shape.memberName.toPascalCase()
        else -> ""
    }

    return "$baseName${member}Schema"
}

private fun serviceOperations(service: ServiceShape, model: Model): List<OperationShape> = TopDownIndex
    .of(model)
    .getContainedOperations(service)
    .toList()

fun KotlinDelegator.useSchemaWriter(shape: Shape, block: (KotlinWriter) -> Unit) {
    val service = ctx.model.expectShape<ServiceShape>(ctx.settings.service)
    val schemaName = schemaName(shape, service)
    val filename = "$schemaName.kt"
    val namespace = "${ctx.settings.pkg.name}.schemas"
    useFileWriter(filename, namespace, block = block)
}

private class ShapeMap private constructor(
    val orderedDependencies: Set<Shape>,
    val recursiveMembers: Set<MemberShape>,
) {
    companion object {
        operator fun invoke(root: Shape, model: Model): ShapeMap {
            fun ShapeId.asShape(): Shape = model.expectShape(this)

            fun shapeDependencies(shape: Shape): List<Shape> = when (shape) {
                is ListShape -> listOf(shape.member)
                is MapShape -> listOf(shape.key, shape.value)
                is MemberShape -> listOf(shape.target.asShape())
                is OperationShape -> listOf(shape.inputShape.asShape(), shape.outputShape.asShape())
                is ServiceShape -> serviceOperations(shape, model) + shape.errorsSet.map { it.asShape() }
                is StructureShape -> shape.members().toList()
                is UnionShape -> shape.members().toList()
                else -> listOf()
            }

            val seen = mutableListOf<Shape>()
            val recursiveMembers = mutableSetOf<MemberShape>()
            val queue = mutableListOf(listOf(root))

            while (queue.isNotEmpty()) {
                val shapePath = queue.removeFirst()
                val shape = shapePath.last()

                fun enqueue(child: Shape) {
                    if (child is MemberShape && child in shapePath) {
                        recursiveMembers.add(child)
                    } else {
                        queue.add(shapePath + child)
                    }
                }

                seen += shape
                shapeDependencies(shape).forEach(::enqueue)
            }

            val orderedDependencies = seen.filterNot { it is MemberShape }.reversed().toSet()
            return ShapeMap(orderedDependencies, recursiveMembers)
        }
    }

}
