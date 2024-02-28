/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolReference
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.core.mangledSuffix
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.StringUtils

/**
 * Get the field descriptor name for a member shape
 */
fun MemberShape.descriptorName(childName: String = ""): String = "${this.defaultName()}${childName}_DESCRIPTOR".uppercase()

/**
 * Get the serializer class name for an operation.
 */
fun OperationShape.serializerName(): String = StringUtils.capitalize(this.id.name) + "OperationSerializer"

/**
 * Get name of the function responsible for serializing an operation's body (paylaod)
 */
fun OperationShape.bodySerializerName(): String = "serialize" + StringUtils.capitalize(this.id.name) + "OperationBody"

/**
 * Get the function responsible for serializing an operation's body (payload) as a [Symbol] and register [block]
 * which will be invoked to actually render the function (signature and implementation)
 */
fun OperationShape.bodySerializer(
    settings: KotlinSettings,
    block: SymbolRenderer,
): Symbol = buildSymbol {
    name = bodySerializerName()
    namespace = settings.pkg.serde
    // place body serializer in same file as operation serializer implementation
    definitionFile = "${serializerName()}.kt"
    renderBy = block
}

/**
 * Get the deserializer class name for an operation. Operation outputs can be deserialized from the protocol (e.g. HTTP)
 * and/or the document/payload.
 */
fun OperationShape.deserializerName(): String = StringUtils.capitalize(this.id.name) + "OperationDeserializer"

/**
 * Get name of the function responsible for deserializing an operation's body (paylaod)
 */
fun OperationShape.bodyDeserializerName(): String = "deserialize" + StringUtils.capitalize(this.id.name) + "OperationBody"

/**
 * Get the function responsible for deserializing an operation's body (payload) as a [Symbol] and register [block]
 * which will be invoked to actually render the function (signature and implementation)
 */
fun OperationShape.bodyDeserializer(
    settings: KotlinSettings,
    block: SymbolRenderer,
): Symbol = buildSymbol {
    name = bodyDeserializerName()
    namespace = settings.pkg.serde
    // place body serializer in same file as operation serializer implementation
    definitionFile = "${deserializerName()}.kt"
    renderBy = block
}

/**
 * Get the serializer class name for a shape bound to the document/payload
 */
// TODO - removed and replace with Shape.documentSerializerName(members: Collection<Members>). SerializeStructGenerator still uses this though
internal fun Symbol.documentSerializerName(): String = "serialize" + StringUtils.capitalize(this.name) + "Document"

/**
 * Get the [Symbol] responsible for serializing the current shape and [members] into the document/payload
 * and register [block] which will be invoked to actually render the function (signature and implementation)
 */
fun Shape.documentSerializer(
    settings: KotlinSettings,
    symbol: Symbol,
    members: Collection<MemberShape> = members(),
    block: SymbolRenderer,
): Symbol {
    val base = symbol.documentSerializerName()
    val suffix = mangledSuffix(members)

    return buildSymbol {
        name = "$base$suffix"
        namespace = settings.pkg.serde
        definitionFile = "${symbol.name}DocumentSerializer.kt"
        reference(symbol, SymbolReference.ContextOption.DECLARE)
        renderBy = block
    }
}

/**
 * Get the deserializer class name for a shape bound to the document/payload
 */
// TODO - removed and replace with Shape.documentDeserializerName(members: Collection<Members>). DeserializeStructGenerator still uses this though
internal fun Symbol.documentDeserializerName(): String = "deserialize" + StringUtils.capitalize(this.name) + "Document"

/**
 * Get the [Symbol] responsible for deserializing the current shape and [members] from the document/payload
 * and register [block] which will be invoked to actually render the function (signature and implementation)
 */
fun Shape.documentDeserializer(
    settings: KotlinSettings,
    symbol: Symbol,
    members: Collection<MemberShape> = members(),
    block: SymbolRenderer,
): Symbol {
    val base = "deserialize" + StringUtils.capitalize(symbol.name) + "Document"
    val suffix = mangledSuffix(members)

    return buildSymbol {
        name = "$base$suffix"
        namespace = settings.pkg.serde
        definitionFile = "${symbol.name}DocumentDeserializer.kt"
        reference(symbol, SymbolReference.ContextOption.DECLARE)
        renderBy = block
    }
}

/**
 * Get the deserializer name for an error shape
 */
fun Symbol.errorDeserializerName(): String = "deserialize" + StringUtils.capitalize(this.name) + "Error"

/**
 * Get the function responsible for deserializing members bound to the payload of an error shape as [Symbol] and
 * register [block] * which will be invoked to actually render the function (signature and implementation)
 */
fun Symbol.errorDeserializer(settings: KotlinSettings, block: SymbolRenderer): Symbol = buildSymbol {
    name = errorDeserializerName()
    namespace = settings.pkg.serde
    val symbol = this@errorDeserializer
    // place it in the same file as the exception deserializer, e.g. for HTTP protocols this will be in
    // same file as HttpDeserialize
    definitionFile = "${symbol.name}Deserializer.kt"
    reference(symbol, SymbolReference.ContextOption.DECLARE)
    renderBy = block
}

/**
 * Get the function responsible for deserializing the specific shape as a standalone payload
 */
fun Shape.payloadDeserializer(
    settings: KotlinSettings,
    symbol: Symbol,
    members: Collection<MemberShape> = members(),
    block: SymbolRenderer,
): Symbol {
    val base = "deserialize" + StringUtils.capitalize(symbol.name) + "Payload"
    val suffix = mangledSuffix(members)
    return buildSymbol {
        name = "$base$suffix"
        namespace = settings.pkg.serde
        definitionFile = "${symbol.name}PayloadDeserializer.kt"
        reference(symbol, SymbolReference.ContextOption.DECLARE)
        renderBy = block
    }
}

/**
 * Get the function responsible for serializing the specific shape as a standalone payload
 */
fun Shape.payloadSerializer(
    settings: KotlinSettings,
    symbol: Symbol,
    members: Collection<MemberShape> = members(),
    block: SymbolRenderer,
): Symbol {
    val base = "serialize" + StringUtils.capitalize(symbol.name) + "Payload"
    val suffix = mangledSuffix(members)
    return buildSymbol {
        name = "$base$suffix"
        namespace = settings.pkg.serde
        definitionFile = "${symbol.name}PayloadSerializer.kt"
        reference(symbol, SymbolReference.ContextOption.DECLARE)
        renderBy = block
    }
}

/**
 * Format an instance of `Instant` using the given [tsFmt]
 * @param paramName The name of the local identifier to format
 * @param tsFmt The timestamp format to use
 * @param forceString Force the result of the expression returned to be a [String] when generated
 */
fun formatInstant(paramName: String, tsFmt: TimestampFormatTrait.Format, forceString: Boolean = false): String = when (tsFmt) {
    TimestampFormatTrait.Format.EPOCH_SECONDS -> {
        // default to epoch seconds as a double
        if (forceString) {
            "$paramName.format(TimestampFormat.EPOCH_SECONDS)"
        } else {
            "$paramName.toEpochDouble()"
        }
    }
    TimestampFormatTrait.Format.DATE_TIME -> "$paramName.format(TimestampFormat.ISO_8601)"
    TimestampFormatTrait.Format.HTTP_DATE -> "$paramName.format(TimestampFormat.RFC_5322)"
    else -> throw CodegenException("unknown timestamp format: $tsFmt")
}

/**
 * return the conversion function `Instant.fromXYZ(paramName)` for the given format
 *
 * @param paramName The name of the local identifier to convert to an `Instant`
 * @param tsFmt The timestamp format [paramName] is expected to be converted from
 */
fun KotlinWriter.parseInstantExpr(paramName: String, tsFmt: TimestampFormatTrait.Format): String {
    val fn = when (tsFmt) {
        TimestampFormatTrait.Format.EPOCH_SECONDS -> "fromEpochSeconds"
        TimestampFormatTrait.Format.DATE_TIME -> "fromIso8601"
        TimestampFormatTrait.Format.HTTP_DATE -> "fromRfc5322"
        else -> throw CodegenException("unknown timestamp format: $tsFmt")
    }
    return format("#T.#L(#L)", RuntimeTypes.Core.Instant, fn, paramName)
}

fun TimestampFormatTrait.Format.toRuntimeEnum(): String = when (this) {
    TimestampFormatTrait.Format.EPOCH_SECONDS -> "TimestampFormat.EPOCH_SECONDS"
    TimestampFormatTrait.Format.DATE_TIME -> "TimestampFormat.ISO_8601"
    TimestampFormatTrait.Format.HTTP_DATE -> "TimestampFormat.RFC_5322"
    else -> throw CodegenException("unknown timestamp format: $this")
}

fun TimestampFormatTrait.Format.toRuntimeEnum(writer: KotlinWriter): String {
    val enum = when (this) {
        TimestampFormatTrait.Format.EPOCH_SECONDS -> "EPOCH_SECONDS"
        TimestampFormatTrait.Format.DATE_TIME -> "ISO_8601"
        TimestampFormatTrait.Format.HTTP_DATE -> "RFC_5322"
        else -> throw CodegenException("unknown timestamp format: $this")
    }
    return writer.format("#T.#L", RuntimeTypes.Core.TimestampFormat, enum)
}

/**
 * Get the serde SerialKind for a shape
 */
fun Shape.serialKind(): String = when (this.type) {
    ShapeType.BOOLEAN -> "SerialKind.Boolean"
    ShapeType.BYTE -> "SerialKind.Byte"
    ShapeType.SHORT -> "SerialKind.Short"
    ShapeType.INTEGER -> "SerialKind.Integer"
    ShapeType.LONG -> "SerialKind.Long"
    ShapeType.FLOAT -> "SerialKind.Float"
    ShapeType.DOUBLE -> "SerialKind.Double"
    ShapeType.STRING -> "SerialKind.String"
    ShapeType.ENUM -> "SerialKind.Enum"
    ShapeType.INT_ENUM -> "SerialKind.IntEnum"
    ShapeType.BLOB -> "SerialKind.Blob"
    ShapeType.TIMESTAMP -> "SerialKind.Timestamp"
    ShapeType.DOCUMENT -> "SerialKind.Document"
    ShapeType.BIG_INTEGER, ShapeType.BIG_DECIMAL -> "SerialKind.BigNumber"
    ShapeType.LIST -> "SerialKind.List"
    ShapeType.SET -> "SerialKind.List"
    ShapeType.MAP -> "SerialKind.Map"
    ShapeType.STRUCTURE -> "SerialKind.Struct"
    ShapeType.UNION -> "SerialKind.Struct"
    else -> throw CodegenException("unknown SerialKind for ${this.type} ($this)")
}

/**
 * Specifies the type of value the identifier represents
 */
internal enum class NestedIdentifierType(val prefix: String) {
    KEY("k"), // Generated variable names for map keys
    VALUE("v"), // Generated variable names for map values
    ELEMENT("el"), // Generated variable name for list elements
    COLLECTION("col"), // Generated variable name for collection types (list, set)
    MAP("map"), // Generated variable name for map type
}

/**
 * Generate an identifier for a given nesting level
 * @param type intended type of value
 */
internal fun Int.variableNameFor(type: NestedIdentifierType): String = "${type.prefix}$this"

/**
 * Generate an identifier for a given nesting level
 */
internal fun Int.nestedDescriptorName(): String = "_C$this"

/**
 * Returns true if the shape can contain other shapes
 */
internal fun Shape.isContainerShape() = when (this) {
    is CollectionShape,
    is MapShape,
    -> true
    else -> false
}

/**
 * Returns [Shape] of the child member of the passed Shape is a collection type or null if not collection type.
 */
internal fun Shape.childShape(model: Model): Shape? = when (this) {
    is CollectionShape -> model.expectShape(this.member.target)
    is MapShape -> model.expectShape(this.value.target)
    else -> null
}

internal fun nullabilitySuffix(isSparse: Boolean): String = if (isSparse) "?" else ""
