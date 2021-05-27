# Document type

* **Type**: Design
* **Author(s)**: Aaron Todd

## Work in progress

**Note**: This design is a work in progress and is subject to change.

# Abstract

This design discusses the treatment of Smithy `document` shapes in Kotlin generated code. It does not cover other shapes
such as `struct`, `union`, etc. which are handled by the [Kotlin Smithy SDK](kotlin-smithy-sdk.md) design.

# Design

The `document` type is an untyped JSON-like value that can take on the following types: null, boolean, string, byte,
short, integer, long, float, double, an array of these types, or a map of these types where the key is string.

This type is best represented as a sum type (sealed class is closest we can get in Kotlin). See the
[JSON type](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElement.kt)
from the kotlinx.serialization lib for an example on which the following is derived. We should provide our own type but
we may be able to internally deal with serialization by going through the one from the kotlinx.serialization library.

````kotlin
package com.amazonaws.smithy.runtime

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
data class SmithyBool(val value: Boolean): Document() {
    override fun toString(): String = when(value) {
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
data class SmithyArray(val content: List<Document>): Document(), List<Document> by content{
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

    override fun toString(): String = content.joinToString( separator = ",", prefix = "[", postfix = "]" )
}

/**
 * Class representing document `map` type
 *
 * Map consists of name-value pairs, where the value is an arbitrary Document. This is much like a JSON object.
 */
data class SmithyMap(val content: Map<String, Document>): Document(), Map<String, Document> by content {
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
            transform = {(k, v) -> """"$k":$v"""}
        )
    }
}

fun Boolean.toDocument() = SmithyBool(this)
fun Number.toDocument() = SmithyNumber(this)
fun String.toDocument() = SmithyString(this)

/**
 * DSL builder for a [SmithyArray]
 */
class DocumentArrayBuilder internal constructor() {
    internal val content: MutableList<Document> = mutableListOf()

    /**
     * Adds [this] value to the current [SmithyArray] as [SmithyString]
     */
    operator fun String.unaryPlus() {
        content.add(SmithyString(this))
    }

    /**
     * Adds [this] value to the current [SmithyArray] as [SmithyBool]
     */
    operator fun Boolean.unaryPlus() {
        content.add(SmithyBool(this))
    }

    /**
     * Adds [this] value to the current [SmithyArray] as [Document]
     */
    operator fun Document.unaryPlus() {
        content.add(this)
    }

    /**
     * Convenience function to wrap raw numeric literals
     *
     * Use as `+n()` inside of [documentArray] builder init().
     */
    fun n(number: Number): SmithyNumber = SmithyNumber(number)
}

/**
 * Builds [SmithyArray] with given [init] builder.
 *
 * NOTE: raw numeric types need to be wrapped as a [SmithyNumber]. Use the [DocumentArrayBuilder::a] builder
 * as a shorthand.
 */
fun documentArray(init: DocumentArrayBuilder.() -> Unit): Document {
    val builder = DocumentArrayBuilder()
    builder.init()
    return SmithyArray(builder.content)
}

/**
 * DSL builder for a [Document] as a [SmithyMap]
 */
class DocumentBuilder internal constructor() {
    internal val content: MutableMap<String, Document> = linkedMapOf()

    /**
     * Adds given [value] as [SmithyBool] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Boolean) {
       require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] as [SmithyNumber] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Number) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] as [SmithyString] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: String) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Document) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value
    }
}

/**
 * Builds [Document] with given [init] builder.
 *
 * ```
 * val doc = document {
 *     "foo" to 1
 *     "baz" to document {
 *         "quux" to documentArray {
 *             +n(202L)
 *             +n(12)
 *             +true
 *             +"blah"
 *         }
 *     }
 *     "foobar" to document {
 *         "nested" to "a string"
 *         "blerg" to documentArray {
 *             +documentArray {
 *                 +n(2.02)
 *              }
 *         }
 *     }
 * }
 * ```
 *
 * This generates the following JSON:
 * {"foo":1,"baz":{"quux":[202,12,true,"blah"]},"foobar":{"nested":"a string","blerg":[[2.02]]}}
 */
fun document(init: DocumentBuilder.() -> Unit): Document {
    val builder = DocumentBuilder()
    builder.init()
    return SmithyMap(builder.content)
}
````

Example usage of building a doc or processing one:

```kotlin
fun foo() {
    val doc = document {
        "foo" to 1
        "baz" to document {
            "quux" to documentArray {
                +n(202L)
                +n(12)
                +true
                +"blah"
            }
        }
        "foobar" to document {
            "nested" to "a string"
            "blerg" to documentArray {
                +documentArray {
                    +n(2.02)
                }
            }
        }
    }
    println(doc)

    processDoc(doc)
}

fun processDoc(doc: Document) {
    when(doc) {
        is SmithyNumber -> println("number: $doc")
        is SmithyBool -> println("bool: ${doc.value}")
        is SmithyString -> println("str: ${doc.value}")
        is SmithyNull -> println("str: $doc")
        is SmithyArray-> {
            println("array")
            for (d in doc) processDoc(d)
        }
        is SmithyMap -> {
            println("map")
            for((k,d) in doc) {
                print("$k: ")
                processDoc(d)
            }
        }
    }
}
```

# Revision history

* 5/27/2021 - Initial upload
