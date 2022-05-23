/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy

import kotlinx.serialization.json.*

/**
 * A `Document` is used to store arbitrary or unstructured data.
 *
 * The provided casting functions (eg. [asInt], [asMap]) allow callers to unwrap contents of the Document at runtime.
 *
 * Document serializes to and from JSON, both over the wire and when converting to or from caller classes that implement
 * [Transformable].
 */
sealed class Document {
    companion object {
        /**
         * Builds a Document using the given [Builder].
         */
        operator fun invoke(init: Builder.() -> Unit): Document {
            val builder = Builder()
            builder.init()
            return Map(builder.content)
        }

        operator fun invoke(value: kotlin.Number): Document = Number(value)
        operator fun invoke(value: kotlin.String): Document = String(value)
        operator fun invoke(value: kotlin.Boolean): Document = Boolean(value)
        operator fun invoke(value: Transformable): Document = fromString(value.serialize())

        /**
         * Creates a Document from a serialized value.
         */
        fun fromString(value: kotlin.String): Document =
            fromJsonElement(
                Json.parseToJsonElement(value)
            )

        private fun fromJsonElement(value: JsonElement): Document =
            when (value) {
                is JsonPrimitive -> fromJsonPrimitive(value)
                is JsonArray -> List(value.map { fromJsonElement(it) })
                is JsonObject -> Map(value.mapValues { fromJsonElement(it.value) })
                JsonNull -> Null
            }

        private fun fromJsonPrimitive(value: JsonPrimitive): Document =
            when {
                value.intOrNull != null -> Number(value.int)
                value.longOrNull != null -> Number(value.long)
                value.floatOrNull != null -> Number(value.float)
                value.doubleOrNull != null -> Number(value.double)
                value.isString -> String(value.content)
                value.booleanOrNull != null -> Boolean(value.boolean)
                else -> throw IllegalArgumentException("json value $value could not be deserialized")
            }

        /**
         * Creates a [List] from the provided values.
         */
        fun listOf(vararg values: Any?): Document =
            List(
                values.map {
                    when (it) {
                        is kotlin.Number -> Number(it)
                        is kotlin.String -> String(it)
                        is kotlin.Boolean -> Boolean(it)
                        is List -> it
                        is Map -> it
                        is Transformable -> fromString(it.serialize())
                        null -> Null
                        else -> throw IllegalArgumentException("incompatible value $it used to construct document list")
                    }
                }
            )
    }

    /**
     * Wraps a [kotlin.Number].
     */
    data class Number(val value: kotlin.Number) : Document() {
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
    data class List(val value: kotlin.collections.List<Document>) : Document() {
        override fun toString() = value.joinToString(separator = ",", prefix = "[", postfix = "]")
    }

    /**
     * Wraps a [kotlin.collections.Map].
     */
    data class Map(val value: kotlin.collections.Map<kotlin.String, Document>) : Document() {
        override fun toString() = value
            .entries
            .joinToString(
                separator = ",",
                prefix = "{",
                postfix = "}",
                transform = { (k, v) -> """"$k":$v""" }
            )
    }

    /**
     * Represents a `null` value.
     */
    object Null : Document() {
        override fun toString() = "null"
    }

    fun asNumber() = (this as Number).value
    fun asString() = (this as String).value
    fun asBoolean() = (this as Boolean).value
    fun asList() = (this as List).value
    fun asMap() = (this as Map).value

    fun asInt() = asNumber().toInt()
    fun asByte() = asNumber().toByte()
    fun asLong() = asNumber().toLong()
    fun asFloat() = asNumber().toFloat()
    fun asDouble() = asNumber().toDouble()

    val isNull: kotlin.Boolean
        get() = this == Null

    operator fun get(i: Int) = asList()[i]
    operator fun get(i: kotlin.String) = asMap()[i]

    /**
     * DSL builder for a [Map]-based [Document].
     */
    class Builder internal constructor() {
        internal val content: MutableMap<kotlin.String, Document> = linkedMapOf()

        infix fun kotlin.String.to(value: kotlin.Number) {
            require(content[this] == null) { "Key $this is already registered in builder" }
            content[this] = Document(value)
        }

        infix fun kotlin.String.to(value: kotlin.Boolean) {
            require(content[this] == null) { "Key $this is already registered in builder" }
            content[this] = Document(value)
        }

        infix fun kotlin.String.to(value: kotlin.String) {
            require(content[this] == null) { "Key $this is already registered in builder" }
            content[this] = Document(value)
        }

        infix fun kotlin.String.to(value: Document) {
            require(content[this] == null) { "Key $this is already registered in builder" }
            content[this] = value
        }

        infix fun kotlin.String.to(value: Transformable) {
            require(content[this] == null) { "Key $this is already registered in builder" }
            content[this] = fromString(value.serialize())
        }
    }

    /**
     * Implemented by structures that can be transformed into a serialized Document.
     */
    interface Transformable {
        /**
         * Returns a JSON-encoded representation of this object.
         */
        fun serialize(): kotlin.String
    }
}
