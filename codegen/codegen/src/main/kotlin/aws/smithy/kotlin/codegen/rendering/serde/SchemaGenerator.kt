/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.InlineKotlinWriter
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.JsonNameTrait
import software.amazon.smithy.model.traits.RequiredTrait
import software.amazon.smithy.model.traits.SparseTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.model.traits.Trait
import software.amazon.smithy.model.traits.XmlNameTrait


private fun String.screamingSnake(): String =
    replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

private val preludeSchemaByType = mapOf(
    ShapeType.BLOB to "Blob",
    ShapeType.BOOLEAN to "Boolean",
    ShapeType.STRING to "String",
    ShapeType.TIMESTAMP to "Timestamp",
    ShapeType.BYTE to "Byte",
    ShapeType.SHORT to "Short",
    ShapeType.INTEGER to "Integer",
    ShapeType.LONG to "Long",
    ShapeType.FLOAT to "Float",
    ShapeType.DOUBLE to "Double",
    ShapeType.BIG_INTEGER to "BigInteger",
    ShapeType.BIG_DECIMAL to "BigDecimal",
    ShapeType.DOCUMENT to "Document",
)

private val renderableTraitIds = setOf(
    "smithy.api#jsonName",
    "smithy.api#xmlName",
    "smithy.api#required",
    "smithy.api#sparse",
    "smithy.api#timestampFormat",
)

private val String.timestampFormatEnum: String
    get() = when (this) {
        "epoch-seconds" -> "EPOCH_SECONDS"
        "date-time" -> "DATE_TIME"
        "http-date" -> "HTTP_DATE"
        else -> error("unknown timestampFormat '$this'")
    }

/**
 * Renders the schema members of a structure or union's companion object per the design doc:
 */
class SchemaGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val shape: Shape,
) {
    init {
        require(shape is StructureShape || shape is UnionShape) {
            "SchemaGenerator only renders structure and union shapes, got ${shape.type}"
        }
    }

    private val members: List<MemberShape> = when (shape) {
        is StructureShape -> shape.members().toList()
        is UnionShape -> shape.members().toList()
        else -> emptyList()
    }

    fun render(writer: KotlinWriter) {
        val ctor = if (shape is UnionShape) Schema.UnionSchema else Schema.StructureSchema
        writer.withBlock("public val SCHEMA: #T = #T(#W) {", "}", ctor, ctor, shapeIdWritable(shape)) {
            renderContainerTraits(shape)
            members.forEach { renderMemberDecl(it) }
        }
        members.forEach { writer.renderMemberVals(it) }
    }

    private fun KotlinWriter.renderMemberDecl(member: MemberShape) {
        writeInline("member(#S, ", member.memberName)
        renderTargetSchema(member)
        renderMemberTraitArgs(member)
        write(")")
    }

    // renders the target-schema argument: prelude singleton, nested list/map, or a struct/union's SCHEMA
    private fun KotlinWriter.renderTargetSchema(member: MemberShape) {
        val target = model.expectShape(member.target)
        val prelude = target.preludeSchemaName
        when {
            prelude != null -> writeInline("#T.#L", Schema.PreludeSchemas, prelude)
            member.isRecursive -> writeInline("lazy { #T.SCHEMA }", symbolProvider.toSymbol(target))
            target is ListShape -> renderListSchema(target)
            target is MapShape -> renderMapSchema(target)
            target is StructureShape || target is UnionShape -> writeInline("#T.SCHEMA", symbolProvider.toSymbol(target))
            else -> renderSimpleSchema(target) // named/trait-bearing simple shape (e.g. enum)
        }
    }

    private fun KotlinWriter.renderListSchema(shape: ListShape) {
        withBlock("#T(#W) {", "}", Schema.ListSchema, shapeIdWritable(shape)) {
            renderContainerTraits(shape)
            renderMemberEntry("element", shape.member)
        }
    }

    private fun KotlinWriter.renderMapSchema(shape: MapShape) {
        withBlock("#T(#W) {", "}", Schema.MapSchema, shapeIdWritable(shape)) {
            renderContainerTraits(shape)
            renderMemberEntry("key", shape.key)
            renderMemberEntry("value", shape.value)
        }
    }

    private fun KotlinWriter.renderSimpleSchema(shape: Shape) {
        writeInline("#T(#W, #T.#L)", Schema.SimpleSchema, shapeIdWritable(shape), Schema.ShapeType, shape.type.name)
    }

    private fun KotlinWriter.renderMemberEntry(entry: String, member: MemberShape) {
        writeInline("#L(", entry)
        renderTargetSchema(member)
        renderMemberTraitArgs(member)
        write(")")
    }

    private fun shapeIdWritable(shape: Shape): InlineKotlinWriter = {
        writeInline("#T(#S)", Schema.shapeId, shape.id.toString())
    }

    private fun KotlinWriter.renderMemberVals(member: MemberShape) {
        val name = member.constName
        write("public val #L: #T = SCHEMA.member(#S)!!", name, Schema.MemberSchema, member.memberName)
        when (model.expectShape(member.target)) {
            is ListShape -> write(
                "public val #L_ELEMENT: #T = (#L.target as #T).element",
                name,
                Schema.MemberSchema,
                name,
                Schema.ListSchema,
            )
            is MapShape -> {
                write("public val #L_KEY: #T = (#L.target as #T).key", name, Schema.MemberSchema, name, Schema.MapSchema)
                write("public val #L_VALUE: #T = (#L.target as #T).value", name, Schema.MemberSchema, name, Schema.MapSchema)
            }
            else -> {}
        }
    }

    private fun KotlinWriter.renderMemberTraitArgs(member: MemberShape) {
        member.eligibleTraits.forEach { trait ->
            writeInline(", #W", traitWritable(trait))
        }
    }

    private fun KotlinWriter.renderContainerTraits(shape: Shape) {
        shape.eligibleTraits.forEach { trait ->
            write("trait(#W)", traitWritable(trait))
        }
    }

    private fun traitWritable(trait: Trait): InlineKotlinWriter = {
        when (trait) {
            is JsonNameTrait -> writeInline("#T(#S)", Schema.Traits.JsonNameTrait, trait.value)
            is XmlNameTrait -> writeInline("#T(#S)", Schema.Traits.XmlNameTrait, trait.value)
            is RequiredTrait -> writeInline("#T", Schema.Traits.RequiredTrait)
            is SparseTrait -> writeInline("#T", Schema.Traits.SparseTrait)
            is TimestampFormatTrait -> writeInline(
                "#T(#T.#L)",
                Schema.Traits.TimestampFormatTrait,
                Schema.Traits.TimestampFormat,
                trait.value.timestampFormatEnum,
            )
            else -> error("no schema renderer for trait ${trait.toShapeId()}")
        }
    }


    private val MemberShape.constName: String
        get() = memberName.toCamelCase().screamingSnake()

    private val MemberShape.isRecursive: Boolean
        get() {
            val target = model.expectShape(target)
            return target.id == shape.id
        }

    private val Shape.eligibleTraits: List<Trait>
        get() = allTraits.values.filter { it.toShapeId().toString() in renderableTraitIds }

    private val Shape.preludeSchemaName: String?
        get() = if (eligibleTraits.isEmpty()) preludeSchemaByType[type] else null
}
