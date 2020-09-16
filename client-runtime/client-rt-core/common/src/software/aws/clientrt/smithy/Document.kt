/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.smithy

/**
 * Class representing a Smithy Document type.
 * Can be a [SmithyNumber], [SmithyBool], [SmithyString], [SmithyNull], [SmithyArray], or [SmithyMap]
 */
sealed class Document {
    /**
     * Checks whether the current element is [SmithyNull]
     */
    val isNull: Boolean
        get() = this == SmithyNull
}

/**
 * Class representing document `null` type.
 */
object SmithyNull : Document() {
    override fun toString(): String = "null"
}

/**
 * Class representing document `bool` type
 */
data class SmithyBool(val value: Boolean) : Document() {
    override fun toString(): String = when (value) {
        true -> "true"
        false -> "false"
    }
}

/**
 * Class representing document `string` type
 */
data class SmithyString(val value: String) : Document() {
    override fun toString(): String {
        return "\"$value\""
    }
}

/**
 * Class representing document numeric types.
 *
 * Creates a Document from a number literal: Int, Long, Short, Byte, Float, Double
 */
class SmithyNumber(val content: Number) : Document() {

    /**
     * Returns the content as a byte which may involve rounding
     */
    val byte: Byte get() = content.toByte()

    /**
     * Returns the content as a int which may involve rounding
     */
    val int: Int get() = content.toInt()

    /**
     * Returns the content as a long which may involve rounding
     */
    val long: Long get() = content.toLong()

    /**
     * Returns the content as a float which may involve rounding
     */
    val float: Float get() = content.toFloat()

    /**
     * Returns the content as a double which may involve rounding
     */
    val double: Double get() = content.toDouble()

    override fun toString(): String = content.toString()
}

/**
 * Class representing document `array` type
 */
data class SmithyArray(val content: List<Document>) : Document(), List<Document> by content {
    /**
     * Returns [index] th element of an array as [SmithyNumber] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getNumber(index: Int) = content[index] as? SmithyNumber

    /**
     * Returns [index] th element of an array as [SmithyBool] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getBoolean(index: Int) = content[index] as? SmithyBool

    /**
     * Returns [index] th element of an array as [SmithyString] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getString(index: Int) = content[index] as? SmithyString

    /**
     * Returns [index] th element of an array as [SmithyArray] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getArray(index: Int) = content[index] as? SmithyArray

    /**
     * Returns [index] th element of an array as [SmithyMap] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getMap(index: Int) = content[index] as? SmithyMap

    override fun toString(): String = content.joinToString(separator = ",", prefix = "[", postfix = "]")
}

/**
 * Class representing document `map` type
 *
 * Map consists of name-value pairs, where the value is an arbitrary Document. This is much like a JSON object.
 */
data class SmithyMap(val content: Map<String, Document>) : Document(), Map<String, Document> by content {

    /**
     * Returns [SmithyNumber] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getNumber(key: String): SmithyNumber? = getValue(key) as? SmithyNumber

    /**
     * Returns [SmithyBool] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getBoolean(key: String): SmithyBool? = getValue(key) as? SmithyBool

    /**
     * Returns [SmithyString] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getString(key: String): SmithyString? = getValue(key) as? SmithyString

    /**
     * Returns [SmithyArray] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getArray(key: String): SmithyArray? = getValue(key) as? SmithyArray

    /**
     * Returns [SmithyMap] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getMap(key: String): SmithyMap? = getValue(key) as? SmithyMap

    override fun toString(): String {
        return content.entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
            transform = { (k, v) -> """"$k":$v""" }
        )
    }
}

fun Boolean.toDocument() = SmithyBool(this)
fun Number.toDocument() = SmithyNumber(this)
fun String.toDocument() = SmithyString(this)
