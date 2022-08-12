/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.smithy

/**
 * A `Document` is used to store arbitrary or unstructured data.
 *
 * The provided casting functions (eg. [asInt], [asMap]) allow callers to unwrap contents of the Document at runtime.
 */
public sealed class Document {
    /**
     * Wraps a [kotlin.Number] of arbitrary precision.
     */
    public data class Number(val value: kotlin.Number) : Document() {
        init {
            if (value is Double && !value.isFinite() || value is Float && !value.isFinite()) {
                throw IllegalArgumentException(
                    "a document number cannot be $value, as its value cannot be preserved across serde",
                )
            }
        }

        override fun toString(): kotlin.String = value.toString()
    }

    /**
     * Wraps a [kotlin.String].
     */
    public data class String(val value: kotlin.String) : Document() {
        override fun toString(): kotlin.String = "\"$value\""
    }

    /**
     * Wraps a [kotlin.Boolean].
     */
    public data class Boolean(val value: kotlin.Boolean) : Document() {
        override fun toString(): kotlin.String = value.toString()
    }

    /**
     * Wraps a [kotlin.collections.List].
     */
    public data class List(val value: kotlin.collections.List<Document?>) :
        Document(), kotlin.collections.List<Document?> by value {
        override fun toString(): kotlin.String = value.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    /**
     * Wraps a [kotlin.collections.Map].
     */
    public data class Map(val value: kotlin.collections.Map<kotlin.String, Document?>) :
        Document(), kotlin.collections.Map<kotlin.String, Document?> by value {
        override fun toString(): kotlin.String = value
            .entries
            .joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
                transform = { (k, v) -> """"$k":$v""" },
            )
    }

    private fun asNumber(): kotlin.Number = (this as Number).value
    private fun asNumberOrNull(): kotlin.Number? = (this as? Number)?.value

    public fun asString(): kotlin.String = (this as String).value
    public fun asStringOrNull(): kotlin.String? = (this as? String)?.value

    public fun asBoolean(): kotlin.Boolean = (this as Boolean).value
    public fun asBooleanOrNull(): kotlin.Boolean? = (this as? Boolean)?.value

    public fun asList(): kotlin.collections.List<Document?> = (this as List).value
    public fun asListOrNull(): kotlin.collections.List<Document?>? = (this as? List)?.value

    public fun asMap(): kotlin.collections.Map<kotlin.String, Document?> = (this as Map).value
    public fun asMapOrNull(): kotlin.collections.Map<kotlin.String, Document?>? = (this as? Map)?.value

    public fun asInt(): Int = asNumber().toInt()
    public fun asIntOrNull(): Int? = asNumberOrNull()?.toInt()

    public fun asByte(): Byte = asNumber().toByte()
    public fun asByteOrNull(): Byte? = asNumberOrNull()?.toByte()

    public fun asShort(): Short = asNumber().toShort()
    public fun asShortOrNull(): Short? = asNumberOrNull()?.toShort()

    public fun asLong(): Long = asNumber().toLong()
    public fun asLongOrNull(): Long? = asNumberOrNull()?.toLong()

    public fun asFloat(): Float = asNumber().toFloat()
    public fun asFloatOrNull(): Float? = asNumberOrNull()?.toFloat()

    public fun asDouble(): Double = asNumber().toDouble()
    public fun asDoubleOrNull(): Double? = asNumberOrNull()?.toDouble()
}

/**
 * Construct a [Document] from a [Number] of arbitrary precision.
 */
public fun Document(value: Number): Document = Document.Number(value)

/**
 * Construct a [Document] from a [String].
 */
public fun Document(value: String): Document = Document.String(value)

/**
 * Construct a [Document] from a [Boolean].
 */
public fun Document(value: Boolean): Document = Document.Boolean(value)

/**
 * Construct a [Document] from a [List].
 */
public fun Document(value: List<Document?>): Document = Document.List(value)

/**
 * Construct a [Document] from a [Map].
 */
public fun Document(value: Map<String, Document?>): Document = Document.Map(value)
