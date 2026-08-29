/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.model.filterEventStreamErrors
import aws.smithy.kotlin.codegen.model.fullNameHintOrDefault
import aws.smithy.kotlin.codegen.model.isEnum
import aws.smithy.kotlin.codegen.model.isNullable
import aws.smithy.kotlin.codegen.utils.toCamelCase
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MapShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.StreamingTrait

// the shape's own schema always occupies this name in the companion object
private const val SCHEMA_PROPERTY = "SCHEMA"

internal fun String.screamingSnake(): String = replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()

internal val Shape.writeFn: String
    get() = when (type) {
        ShapeType.BOOLEAN -> "writeBoolean"
        ShapeType.BYTE -> "writeByte"
        ShapeType.SHORT -> "writeShort"
        ShapeType.INTEGER, ShapeType.INT_ENUM -> "writeInt"
        ShapeType.LONG -> "writeLong"
        ShapeType.FLOAT -> "writeFloat"
        ShapeType.DOUBLE -> "writeDouble"
        ShapeType.BIG_INTEGER -> "writeBigInteger"
        ShapeType.BIG_DECIMAL -> "writeBigDecimal"
        ShapeType.STRING, ShapeType.ENUM -> "writeString"
        ShapeType.BLOB -> "writeBlob"
        ShapeType.TIMESTAMP -> "writeTimestamp"
        ShapeType.DOCUMENT -> "writeDocument"
        else -> error("no write function for shape type $type")
    }

internal val Shape.readFn: String
    get() = when (type) {
        ShapeType.BOOLEAN -> "readBoolean"
        ShapeType.BYTE -> "readByte"
        ShapeType.SHORT -> "readShort"
        ShapeType.INTEGER, ShapeType.INT_ENUM -> "readInt"
        ShapeType.LONG -> "readLong"
        ShapeType.FLOAT -> "readFloat"
        ShapeType.DOUBLE -> "readDouble"
        ShapeType.BIG_INTEGER -> "readBigInteger"
        ShapeType.BIG_DECIMAL -> "readBigDecimal"
        ShapeType.STRING, ShapeType.ENUM -> "readString"
        ShapeType.BLOB -> "readBlob"
        ShapeType.TIMESTAMP -> "readTimestamp"
        ShapeType.DOCUMENT -> "readDocument"
        else -> error("no read function for shape type $type")
    }

/**
 * The expression that narrows [accessor] — a value of the Kotlin type generated for this shape — to the type the
 * matching [writeFn] accepts.
 *
 * Enums and intEnums are generated as sealed classes over a raw `value`, which is what actually goes over the wire;
 * every other shape is already wire-shaped.
 */
internal fun Shape.wireValueExpr(accessor: String): String = if (isEnum) "$accessor.value" else accessor

/**
 * The inverse of [wireValueExpr]: the expression that widens [wireExpr] — a value of the type the matching [readFn]
 * returns — to the Kotlin type generated for this shape.
 *
 * Use this directly when the wire value does not come from a `readXxx` call, e.g. a map key handed to a
 * `MapConsumer` (the wire key is always a `String`, so an enum-keyed map converts each key in generated code).
 */
internal fun Shape.kotlinValueExpr(symbolProvider: SymbolProvider, wireExpr: String): String = if (isEnum) {
    "${symbolProvider.toSymbol(this).fullNameHintOrDefault}.fromValue($wireExpr)"
} else {
    wireExpr
}

/**
 * The complete expression that reads a value of this shape out of the deserializer named [de] using the member
 * schema named [schemaRef], already widened to the generated Kotlin type.
 *
 * Only valid for simple shapes; aggregates are read by a nested walk rather than a single `readXxx` call.
 */
internal fun Shape.readValueExpr(symbolProvider: SymbolProvider, de: String, schemaRef: String): String = kotlinValueExpr(symbolProvider, "$de.$readFn($schemaRef)")

/**
 * The fully qualified Kotlin type generated for this member, including its nullability. Collections carry their type
 * arguments, so this is safe to emit as an explicit type argument without adding an import.
 *
 * The type comes from the member rather than its target because only the member knows whether the slot is nullable:
 * a `@sparse` collection's slot is, and so is a slot holding a document, whose value may legally be null.
 */
internal fun MemberShape.kotlinTypeName(symbolProvider: SymbolProvider): String {
    val symbol = symbolProvider.toSymbol(this)
    return if (symbol.isNullable) "${symbol.fullNameHintOrDefault}?" else symbol.fullNameHintOrDefault
}

/**
 * The members of this structure or union that have a generated Kotlin declaration — a property on the structure or a
 * variant of the sealed class — in a single stable order.
 *
 * Every generator that emits or references anything per member MUST source its members here. An event stream
 * (`@streaming union`) drops the variants that target errors, since those surface as thrown exceptions instead of
 * events, so a schema member or a `when` branch naming such a variant would reference a class that does not exist.
 */
internal fun Shape.declaredMembers(model: Model): List<MemberShape> = when (this) {
    is UnionShape -> this.filterEventStreamErrors(model)
    else -> members()
}.sortedBy { it.defaultName() }

/**
 * The subset of [declaredMembers] that the generated serialize/deserialize walks visit.
 *
 * Streaming payloads are excluded: they are handled by the HTTP binding layer, which reads `@streaming` and
 * `@httpPayload` off the schema member, so the member stays in the schema but has nothing the walk can write.
 */
internal fun Shape.serdeMembers(model: Model): List<MemberShape> = declaredMembers(model).filterNot { it.isStreamingPayload(model) }

/**
 * True if this member is a streaming payload, whose generated type is a `ByteStream` or a `Flow` rather than the
 * document type of its target shape.
 */
internal fun MemberShape.isStreamingPayload(model: Model): Boolean = getMemberTrait(model, StreamingTrait::class.java).isPresent

/**
 * The names of the companion object constants holding a structure's or union's member schemas.
 *
 * A name is derived from the member name, but must not collide with `SCHEMA`, with another member's name, with the
 * names synthesized for the members of a nested list or map, or with any type the companion names without
 * qualification — a `val` shadows a classifier of the same name for the whole class body, so a `val B` next to a
 * reference to `B.SCHEMA` breaks the reference. Colliding candidates are deconflicted with an ordinal suffix.
 *
 * Every generator that emits or reads these constants builds its own instance from the same shape; the derivation
 * depends on nothing else, so all of them agree.
 */
internal class MemberSchemaNames(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    shape: Shape,
) {
    private val taken = mutableSetOf(SCHEMA_PROPERTY)
    private val byMemberName = mutableMapOf<String, String>()
    private val byNestedSlot = mutableMapOf<String, String>()

    init {
        val members = shape.declaredMembers(model)
        taken += symbolProvider.toSymbol(shape).name
        members.forEach { reserveClassifiers(model.expectShape(it.target)) }
        members.forEach { member ->
            val name = claim(member.memberName.toCamelCase().screamingSnake())
            byMemberName[member.memberName] = name
            claimNested(name, model.expectShape(member.target))
        }
    }

    /** The constant holding [member]'s own member schema. */
    operator fun get(member: MemberShape): String = byMemberName.getValue(member.memberName)

    /** The constant holding the element member of the list held by the member named [parent]. */
    fun element(parent: String): String = byNestedSlot.getValue("$parent element")

    /** The constant holding the key member of the map held by the member named [parent]. */
    fun key(parent: String): String = byNestedSlot.getValue("$parent key")

    /** The constant holding the value member of the map held by the member named [parent]. */
    fun value(parent: String): String = byNestedSlot.getValue("$parent value")

    // map keys are always strings, so only the element and value slots can nest further
    private fun claimNested(parent: String, target: Shape) {
        when (target) {
            is ListShape -> {
                val elementName = claim("${parent}_ELEMENT")
                byNestedSlot["$parent element"] = elementName
                claimNested(elementName, model.expectShape(target.member.target))
            }
            is MapShape -> {
                val keyName = claim("${parent}_KEY")
                val valueName = claim("${parent}_VALUE")
                byNestedSlot["$parent key"] = keyName
                byNestedSlot["$parent value"] = valueName
                claimNested(valueName, model.expectShape(target.value.target))
            }
            else -> {}
        }
    }

    // structure, union and enum targets are named by their simple name in the companion; collections name only
    // whatever they contain
    private fun reserveClassifiers(target: Shape) {
        when {
            target is ListShape -> reserveClassifiers(model.expectShape(target.member.target))
            target is MapShape -> {
                reserveClassifiers(model.expectShape(target.key.target))
                reserveClassifiers(model.expectShape(target.value.target))
            }
            target is StructureShape || target is UnionShape || target.isEnum -> taken += symbolProvider.toSymbol(target).name
        }
    }

    private fun claim(candidate: String): String {
        var name = candidate
        var ordinal = 2
        while (!taken.add(name)) {
            name = "${candidate}_$ordinal"
            ordinal++
        }
        return name
    }
}
