/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy

/**
 * A `Document` is used to store arbitrary or unstructured data.
 *
 * The provided casting functions (eg. [asInt], [asMap]) allow callers to unwrap contents of the Document at runtime.
 */
sealed class Document {
    /**
     * Wraps a [kotlin.Number] of arbitrary precision.
     */
    data class Number(val value: kotlin.Number) : Document() {
        init {
            if (value is Double && !value.isFinite() || value is Float && !value.isFinite()) {
                throw IllegalArgumentException(
                    "a document number cannot be $value, as its value cannot be preserved across serde"
                )
            }
        }

        override fun toString() = value.toString()
    }

    /**
     * Wraps a [kotlin.String].
     */
    data class String(val value: kotlin.String) : Document() {
        override fun toString() = "\"$value\""
    }

    /**
     * Wraps a [kotlin.Boolean].
     */
    data class Boolean(val value: kotlin.Boolean) : Document() {
        override fun toString() = value.toString()
    }

    /**
     * Wraps a [kotlin.collections.List].
     */
    data class List(val value: kotlin.collections.List<Document?>) :
        Document(), kotlin.collections.List<Document?> by value {
        override fun toString() = value.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    /**
     * Wraps a [kotlin.collections.Map].
     */
    data class Map(val value: kotlin.collections.Map<kotlin.String, Document?>) :
        Document(), kotlin.collections.Map<kotlin.String, Document?> by value {
        override fun toString() = value
            .entries
            .joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
                transform = { (k, v) -> """"$k":$v""" }
            )
    }

    private fun asNumber(): kotlin.Number = (this as Number).value
    private fun asNumberOrNull(): kotlin.Number? = (this as? Number)?.value

    fun asString(): kotlin.String = (this as String).value
    fun asStringOrNull(): kotlin.String? = (this as? String)?.value

    fun asBoolean(): kotlin.Boolean = (this as Boolean).value
    fun asBooleanOrNull(): kotlin.Boolean? = (this as? Boolean)?.value

    fun asList(): kotlin.collections.List<Document?> = (this as List).value
    fun asListOrNull(): kotlin.collections.List<Document?>? = (this as? List)?.value

    fun asMap(): kotlin.collections.Map<kotlin.String, Document?> = (this as Map).value
    fun asMapOrNull(): kotlin.collections.Map<kotlin.String, Document?>? = (this as? Map)?.value

    fun asInt(): Int = asNumber().toInt()
    fun asIntOrNull(): Int? = asNumberOrNull()?.toInt()

    fun asByte(): Byte = asNumber().toByte()
    fun asByteOrNull(): Byte? = asNumberOrNull()?.toByte()

    fun asShort(): Short = asNumber().toShort()
    fun asShortOrNull(): Short? = asNumberOrNull()?.toShort()

    fun asLong(): Long = asNumber().toLong()
    fun asLongOrNull(): Long? = asNumberOrNull()?.toLong()

    fun asFloat(): Float = asNumber().toFloat()
    fun asFloatOrNull(): Float? = asNumberOrNull()?.toFloat()

    fun asDouble(): Double = asNumber().toDouble()
    fun asDoubleOrNull(): Double? = asNumberOrNull()?.toDouble()
}

/**
 * Construct a [Document] from a [Number] of arbitrary precision.
 */
fun Document(value: Number): Document = Document.Number(value)

/**
 * Construct a [Document] from a [String].
 */
fun Document(value: String): Document = Document.String(value)

/**
 * Construct a [Document] from a [Boolean].
 */
fun Document(value: Boolean): Document = Document.Boolean(value)

/**
 * Construct a [Document] from a [List].
 */
fun Document(value: List<Document?>): Document = Document.List(value)

/**
 * Construct a [Document] from a [Map].
 */
fun Document(value: Map<String, Document?>): Document = Document.Map(value)
