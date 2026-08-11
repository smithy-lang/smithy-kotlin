/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.InlineKotlinWriter
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes.Serde.Schema
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.core.withInlineBlock
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.Trait

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

/**
 * Renders the schema members of a structure or union's companion object per the design doc:
 */
class SchemaGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val shape: Shape,
    private val traitExtension: SchemaTraitExtension = SchemaTraitExtension.default(),
) {
    init {
        // TODO: support top-level enum/simple shapes so enums can carry their own companion SCHEMA
        //  (SEP: any exported type MUST have an exported schema). Simple/list/map shapes are already
        //  rendered as member targets; relaxing this require + a top-level entry point would cover enums.
        require(shape is StructureShape || shape is UnionShape) {
            "SchemaGenerator only renders structure and union shapes, got ${shape.type}"
        }
    }

    private val members: List<MemberShape> = when (shape) {
        is StructureShape -> shape.members().toList()
        is UnionShape -> shape.members().toList()
        else -> emptyList()
    }

    /** Renders `SCHEMA` + extracted member vals (call inside the companion object). */
    fun render(writer: KotlinWriter) {
        val ctor = if (shape is UnionShape) Schema.UnionSchema else Schema.StructureSchema
        writer.withBlock("public val SCHEMA: #T = #T(#W) {", "}", ctor, ctor, shapeIdWritable(shape)) {
            renderContainerTraits(shape)
            members.forEach { renderMemberDecl(it) }
        }
        members.forEach { writer.renderMemberVals(it) }
    }

    /** Renders `serialize`/`serializeMembers`. */
    fun renderSerialize(writer: KotlinWriter): Unit =
        ShapeSerializerGenerator(model, symbolProvider, shape).render(writer)

    /** Renders `deserialize` (call on the Builder for structs, or in the companion for unions). */
    fun renderDeserialize(writer: KotlinWriter): Unit =
        ShapeDeserializerGenerator(model, symbolProvider, shape).render(writer)

    // ── SCHEMA value ──────────────────────────────────────────────────────────────────────────────

    private fun KotlinWriter.renderMemberDecl(member: MemberShape) {
        writeInline("member(#S, ", member.memberName)
        renderTargetSchema(member)
        renderTraitArgs(member)
        writeInline(")")
        ensureNewline()
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
        withInlineBlock("#T(#W) {", "}", Schema.ListSchema, shapeIdWritable(shape)) {
            renderContainerTraits(shape)
            renderMemberEntry("element", shape.member)
        }
    }

    private fun KotlinWriter.renderMapSchema(shape: MapShape) {
        withInlineBlock("#T(#W) {", "}", Schema.MapSchema, shapeIdWritable(shape)) {
            renderContainerTraits(shape)
            renderMemberEntry("key", shape.key)
            renderMemberEntry("value", shape.value)
        }
    }

    private fun KotlinWriter.renderSimpleSchema(shape: Shape) {
        writeInline("#T(#W, #T.#L", Schema.SimpleSchema, shapeIdWritable(shape), Schema.ShapeType, shape.type.name)
        renderTraitArgs(shape)
        writeInline(")")
    }

    private fun KotlinWriter.renderMemberEntry(entry: String, member: MemberShape) {
        ensureNewline()
        writeInline("#L(", entry)
        renderTargetSchema(member)
        renderTraitArgs(member)
        writeInline(")")
    }

    private fun shapeIdWritable(shape: Shape): InlineKotlinWriter = {
        writeInline("#T(#S)", Schema.shapeId, shape.id.toString())
    }

    private fun KotlinWriter.renderMemberVals(member: MemberShape) {
        val name = member.constName
        write("public val #L: #T = SCHEMA.member(#S)!!", name, Schema.MemberSchema, member.memberName)
        renderNestedMemberVals(name, model.expectShape(member.target))
    }

    private fun KotlinWriter.renderNestedMemberVals(parent: String, target: Shape) {
        when (target) {
            is ListShape -> {
                val elem = "${parent}_ELEMENT"
                write("public val #L: #T = (#L.target as #T).element", elem, Schema.MemberSchema, parent, Schema.ListSchema)
                renderNestedMemberVals(elem, model.expectShape(target.member.target))
            }
            is MapShape -> {
                val key = "${parent}_KEY"
                val value = "${parent}_VALUE"
                write("public val #L: #T = (#L.target as #T).key", key, Schema.MemberSchema, parent, Schema.MapSchema)
                write("public val #L: #T = (#L.target as #T).value", value, Schema.MemberSchema, parent, Schema.MapSchema)
                renderNestedMemberVals(value, model.expectShape(target.value.target))
            }
            else -> {}
        }
    }

    private fun KotlinWriter.renderTraitArgs(shape: Shape) {
        shape.eligibleTraits.forEach { trait ->
            writeInline(", #W", traitWritable(trait))
        }
    }

    private fun KotlinWriter.renderContainerTraits(shape: Shape) {
        shape.eligibleTraits.forEach { trait ->
            write("trait(#W)", traitWritable(trait))
        }
    }

    private fun traitWritable(trait: Trait): InlineKotlinWriter = traitExtension.rendererFor(trait.toShapeId())?.render(trait)
        ?: error("no schema trait renderer registered for ${trait.toShapeId()}")

    // TODO: handle unknown/custom traits. Per the SEP ("Handling unknown traits in code generation") an
    //  unrecognized trait SHOULD be emitted into the schema as-is, keyed by ShapeId with its node value
    //  represented as a Document (DocumentTrait). Today such traits are filtered out by [eligibleTraits]
    //  (no registered renderer -> not in includedTraitIds) and silently dropped. Implementing the default
    //  requires reintroducing DocumentTrait + the Document value representation, which are deferred.

    private val MemberShape.isRecursive: Boolean
        get() {
            val target = model.expectShape(target)
            return target.id == shape.id
        }

    // NOTE: only traits with a registered renderer are included; unknown/custom traits are dropped here.
    // See the TODO on [traitWritable] — the SEP wants unknowns emitted as a DocumentTrait once Document lands.
    private val Shape.eligibleTraits: List<Trait>
        get() = allTraits.values.filter { it.toShapeId() in traitExtension.includedTraitIds }

    private val Shape.preludeSchemaName: String?
        get() = if (eligibleTraits.isEmpty()) preludeSchemaByType[type] else null
}
