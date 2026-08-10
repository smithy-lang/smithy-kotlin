/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.ListSchema
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.MapSchema
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.MemberSchema
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.PreludeSchemas
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.ShapeType as SchemaShapeType
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.SimpleSchema
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.StructureSchema
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.Traits.JsonNameTrait as SchemaJsonNameTrait
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.Traits.RequiredTrait as SchemaRequiredTrait
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.Traits.SparseTrait as SchemaSparseTrait
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.Traits.TimestampFormat as SchemaTimestampFormat
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.Traits.TimestampFormatTrait as SchemaTimestampFormatTrait
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.Traits.XmlNameTrait as SchemaXmlNameTrait
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.UnionSchema
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema.shapeId as shapeIdFn
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
        val schemaInterface = if (shape is UnionShape) UnionSchema else StructureSchema
        writer.writeInline("public val SCHEMA: #T = ", schemaInterface)
        writer.renderContainerSchema(shape, members)
        members.forEach { writer.renderMemberVals(it) }
    }

    // ── SCHEMA value ──────────────────────────────────────────────────────────────────────────────

    private fun KotlinWriter.renderContainerSchema(shape: Shape, members: List<MemberShape>) {
        val ctor = if (shape is UnionShape) UnionSchema else StructureSchema
        writeInline("#T(", ctor)
        renderShapeId(shape)
        withBlock(") {", "}") {
            renderContainerTraits(shape)
            members.forEach { renderMemberDecl(it) }
        }
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
            prelude != null -> writeInline("#T.#L", PreludeSchemas, prelude)
            member.isRecursive -> writeInline("lazy { #T.SCHEMA }", symbolProvider.toSymbol(target))
            target is ListShape -> renderListSchema(target)
            target is MapShape -> renderMapSchema(target)
            target is StructureShape || target is UnionShape -> writeInline("#T.SCHEMA", symbolProvider.toSymbol(target))
            else -> renderSimpleSchema(target) // named/trait-bearing simple shape (e.g. enum)
        }
    }

    private fun KotlinWriter.renderListSchema(shape: ListShape) {
        writeInline("#T(", ListSchema)
        renderShapeId(shape)
        withBlock(") {", "}") {
            renderContainerTraits(shape)
            writeInline("element(")
            renderTargetSchema(shape.member)
            renderMemberTraitArgs(shape.member)
            write(")")
        }
    }

    private fun KotlinWriter.renderMapSchema(shape: MapShape) {
        writeInline("#T(", MapSchema)
        renderShapeId(shape)
        withBlock(") {", "}") {
            renderContainerTraits(shape)
            writeInline("key(")
            renderTargetSchema(shape.key)
            renderMemberTraitArgs(shape.key)
            write(")")
            writeInline("value(")
            renderTargetSchema(shape.value)
            renderMemberTraitArgs(shape.value)
            write(")")
        }
    }

    private fun KotlinWriter.renderSimpleSchema(shape: Shape) {
        writeInline("#T(", SimpleSchema)
        renderShapeId(shape)
        writeInline(", #T.#L)", SchemaShapeType, shape.type.name)
    }

    private fun KotlinWriter.renderShapeId(shape: Shape) {
        writeInline("#T(#S)", shapeIdFn, shape.id.toString())
    }

    // ── extracted member vals ─────────────────────────────────────────────────────────────────────

    // NAME = SCHEMA.member("name")!!  (+ element/key/value accessors for inlined list/map members)
    private fun KotlinWriter.renderMemberVals(member: MemberShape) {
        val name = member.constName
        write("public val #L: #T = SCHEMA.member(#S)!!", name, MemberSchema, member.memberName)
        when (model.expectShape(member.target)) {
            is ListShape -> write(
                "public val #L_ELEMENT: #T = (#L.target as #T).element",
                name,
                MemberSchema,
                name,
                ListSchema,
            )
            is MapShape -> {
                write("public val #L_KEY: #T = (#L.target as #T).key", name, MemberSchema, name, MapSchema)
                write("public val #L_VALUE: #T = (#L.target as #T).value", name, MemberSchema, name, MapSchema)
            }
            else -> {}
        }
    }

    // ── traits ──────────────────────────────────────────────────────────────────────────────────

    private fun KotlinWriter.renderMemberTraitArgs(member: MemberShape) {
        member.eligibleTraits.forEach { trait ->
            writeInline(", ")
            renderTrait(trait)
        }
    }

    private fun KotlinWriter.renderContainerTraits(shape: Shape) {
        shape.eligibleTraits.forEach { trait ->
            writeInline("trait(")
            renderTrait(trait)
            write(")")
        }
    }

    private fun KotlinWriter.renderTrait(trait: Trait) {
        when (trait) {
            is JsonNameTrait -> writeInline("#T(#S)", SchemaJsonNameTrait, trait.value)
            is XmlNameTrait -> writeInline("#T(#S)", SchemaXmlNameTrait, trait.value)
            is RequiredTrait -> writeInline("#T", SchemaRequiredTrait)
            is SparseTrait -> writeInline("#T", SchemaSparseTrait)
            is TimestampFormatTrait -> writeInline(
                "#T(#T.#L)",
                SchemaTimestampFormatTrait,
                SchemaTimestampFormat,
                trait.value.timestampFormatEnum,
            )
            else -> error("no schema renderer for trait ${trait.toShapeId()}")
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private val MemberShape.constName: String
        get() = memberName.toCamelCase().replaceFirstChar { it.uppercaseChar() }.screamingSnake()

    private val MemberShape.isRecursive: Boolean
        get() {
            // a direct self-reference: the member's target is the shape whose companion we're rendering
            val target = model.expectShape(target)
            return target.id == shape.id
        }

    private val Shape.eligibleTraits: List<Trait>
        get() = allTraits.values.filter { it.toShapeId().toString() in renderableTraitIds }

    private val Shape.preludeSchemaName: String?
        get() = if (eligibleTraits.isEmpty()) preludeSchemaByType[type] else null
}

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
