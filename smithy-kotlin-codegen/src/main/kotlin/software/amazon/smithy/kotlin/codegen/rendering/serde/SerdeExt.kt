/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeType
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.utils.StringUtils

/**
 * Get the field descriptor name for a member shape
 */
fun MemberShape.descriptorName(childName: String = ""): String = "${this.defaultName()}${childName}_DESCRIPTOR".toUpperCase()

/**
 * Get the serializer class name for an operation. Operation inputs can be serialized to the protocol (e.g. HTTP)
 * and/or to the document/payload. Distinguishing from generic
 */
fun OperationShape.serializerName(): String = StringUtils.capitalize(this.id.name) + "OperationSerializer"

/**
 * Get the deserializer class name for an operation. Operation outputs can be deserialized from the protocol (e.g. HTTP)
 * and/or the document/payload.
 */
fun OperationShape.deserializerName(): String = StringUtils.capitalize(this.id.name) + "OperationDeserializer"

/**
 * Get the serializer class name for a shape bound to the document/payload
 */
fun Symbol.documentSerializerName(): String = StringUtils.capitalize(this.name) + "DocumentSerializer"

/**
 * Get the deserializer class name for a shape bound to the document/payload
 */
fun Symbol.documentDeserializerName(): String = StringUtils.capitalize(this.name) + "DocumentDeserializer"

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
fun parseInstant(paramName: String, tsFmt: TimestampFormatTrait.Format): String = when (tsFmt) {
    TimestampFormatTrait.Format.EPOCH_SECONDS -> "Instant.fromEpochSeconds($paramName)"
    TimestampFormatTrait.Format.DATE_TIME -> "Instant.fromIso8601($paramName)"
    TimestampFormatTrait.Format.HTTP_DATE -> "Instant.fromRfc5322($paramName)"
    else -> throw CodegenException("unknown timestamp format: $tsFmt")
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
    MAP("map"); // Generated variable name for map type
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
