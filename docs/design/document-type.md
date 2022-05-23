# Document type

* **Type**: Design
* **Author(s)**: Aaron Todd, Luc Talatinian

## Work in progress

**Note**: This design is a work in progress and is subject to change.

# Abstract

This design discusses the treatment of Smithy `document` shapes in Kotlin generated code. It does not cover other shapes
such as `struct`, `union`, etc. which are handled by the [Kotlin Smithy SDK](kotlin-smithy-sdk.md) design.

# Design

The `document` type is an untyped JSON-like value that can take on the following types:
* null
* boolean
* string
* number (byte, short, integer, long, float, double)
* an array of these types
* a map of these types, keyed by string

This type is best represented as a sum type (sealed class is closest we can get in Kotlin). See the
[JSON type](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElement.kt)
from the kotlinx.serialization lib for an example on which the following is derived. We expose our own type for SDK
consumers, but internally consume kotlinx.serialization to unwrap serialized values passed during construction.

# Interface (annotated)
````kotlin
package aws.smithy.kotlin.runtime.smithy

sealed class Document {
    companion object {
        /**
         * Overloads to create a document from possible values.
         */
        operator fun invoke(init: Builder.() -> Unit): Document
        operator fun invoke(value: kotlin.Number): Document
        operator fun invoke(value: kotlin.String): Document
        operator fun invoke(value: kotlin.Boolean): Document
        operator fun invoke(value: Transformable): Document

        /**
         * Unwraps a Document from a serialized value.
         */
        fun fromString(value: kotlin.String): Document
        
        /**
         * Creates a Document list from the provided values.
         */
        fun listOf(vararg values: Any?): Document
    }

    /**
     * Inheritors that wrap all the possible values a Document can hold.
     */
    data class Number(val value: kotlin.Number) : Document()
    data class String(val value: kotlin.String) : Document()
    data class Boolean(val value: kotlin.Boolean) : Document()
    data class List(val value: kotlin.collections.List<Document>) : Document()
    data class Map(val value: kotlin.collections.Map<kotlin.String, Document>) : Document()
    object Null : Document()

    /**
     * A host of casts to simplify in-memory processing.
     */
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

    /**
     * Simplify access to list/object members.
     */
    operator fun get(i: Int) = asList()[i]
    operator fun get(i: kotlin.String) = asMap()[i]

    /**
     * DSL builder to create a map-based Document.
     */
    class Builder internal constructor() {
        infix fun kotlin.String.to(value: kotlin.Number)
        infix fun kotlin.String.to(value: kotlin.Boolean)
        infix fun kotlin.String.to(value: kotlin.String)
        infix fun kotlin.String.to(value: Document)
        infix fun kotlin.String.to(value: Transformable)
    }

    /**
     * Implemented by structures that can be transformed into a serialized Document.
     * Must serialize to JSON.
     */
    interface Transformable {
        fun serialize(): kotlin.String
    }
}
````

Example usage of building a doc or processing one:

```kotlin
import kotlinx.serialization.json.*

// implement Document.Transformable so we can use this in document builder
// use kotlinx.serialization
@Serializable
data class Clazz(
    val name: String,
    val ival: Int
    ) : Document.Transformable {
    
    override fun serialize() = Json.encodeToString(this)
}

fun main() {
    val meta = Document {
        "foo" to 1
        "baz" to Document {
            "quux" to Document.listOf(
                202L,
                12,
                false,
                true,
                "blah",
                null,
                Document.listOf(1, null, 2)
            )
        }
        "foobar" to Document {
            "nested" to "a string"
            "blerg" to Document.listOf(
                Document.listOf(2.02)
            )
        }
        "qux4" to Clazz("this", 79)
        "qux3" to Document.listOf(
            11,20,3,4,9
        )
    }

    println(doc["foo"]) // 1
    println(doc["qux4"]?.get("ival")?.asInt()?.plus(2)) // 81
    
    val metaEx = Clazz("metadata_", 7)
    DocumentFooClient.fromEnvironment().use {
        val response = it.CreateItem {
            itemName = "sample"
            // pass an in-memory Document
            metadata = meta
            // pass a Transformable
            metadataEx = Document(metaEx)
        }
        
        // pull a value from a returned Document
        val metaName = response.createdItemMetadata["name"]?.asString()
        // pull a returned Document back into a known structure
        val returnedMetadata = Json.decodeFromString<Clazz>(
            response.createdItemMetadata.toString()
        )
    }
}
```

# Revision history

* 5/27/2021 - Initial upload
* 5/22/2022 - Refine/extend structure and use of interface