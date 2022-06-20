# Document type

* **Type**: Design
* **Author(s)**: Aaron Todd, Luc Talatinian

# Abstract

This design discusses the treatment of Smithy `document` shapes in Kotlin generated code. It does not cover other shapes
such as `struct`, `union`, etc. which are handled by the [Kotlin Smithy SDK](kotlin-smithy-sdk.md) design.

# Design

The `document` type is an untyped JSON-like value that can take on the following types:
* null
* boolean
* string
* number of arbitrary precision (eg. byte, int, long, double)
* an array of these types
* a map of these types, keyed by string

This type is best represented as a sum type (sealed class is closest we can get in Kotlin). See the
[JSON type](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElement.kt)
from the kotlinx.serialization lib for an example on which the following is derived.

# Document
The Document shape will be represented as a sealed class in Kotlin with each variant (subclass) representing one of the
possible values the type can hold.

List and Map subtypes implement their corresponding `kotlin.collections` interfaces.

Included for ease of use are a host of basic typecasts, eg. `asInt` (and its `asIntOrNull` counterpart). These types of
convenience getters can be expanded/evolved based on future user feedback.

```kotlin
package aws.smithy.kotlin.runtime.smithy

sealed class Document {
    data class Number(val value: kotlin.Number) : Document()
    data class String(val value: kotlin.String) : Document()
    data class Boolean(val value: kotlin.Boolean) : Document()
    data class List(val value: kotlin.collections.List<Document?>) :
        Document(), kotlin.collections.List<Document?> by value
    data class Map(val value: kotlin.collections.Map<kotlin.String, Document?>) :
        Document(), kotlin.collections.Map<kotlin.String, Document?> by value

    fun asString(): kotlin.String
    fun asStringOrNull(): kotlin.String?
    fun asInt(): Int
    fun asIntOrNull(): Int?
    // etc...
}
```

# DocumentBuilder
A DSL builder is exposed for idiomatic construction of arbitrary documents.
```kotlin
package aws.smithy.kotlin.runtime.smithy

class DocumentBuilder internal constructor() {
    infix fun String.to(value: Number?)
    infix fun String.to(value: String?)
    infix fun String.to(value: Boolean?)
    infix fun String.to(value: Document?)
    infix fun String.to(value: Nothing?)

    class ListBuilder internal constructor() {
        fun add(value: Number?)
        fun add(value: String?)
        fun add(value: Boolean?)
        fun add(value: Document?)
        fun add(value: Nothing?)

        fun addAll(value: List<Number?>)
        fun addAll(value: List<String?>)
        fun addAll(value: List<Boolean?>)
        fun addAll(value: List<Document?>)
    }

    fun buildList(init: ListBuilder.() -> Unit): Document
}

fun buildDocument(init: DocumentBuilder.() -> Unit): Document
```

Example usage of building a doc or processing one:

```kotlin
fun main() {
    val doc = buildDocument {
        "foo" to 1
        "baz" to buildList {
            add(202L)
            add(12)
            add(true)
            add("blah")
            add(null)
        }
        "qux" to null
    }
    
    println(doc.asMap()["foo"]) // 1
}
```

# Revision history

* 5/27/2021 - Initial upload
* 5/22/2022 - Refine/extend structure and use of interface
* 6/16/2022 - Refactor nullability to use kotlin instead of explicit subclass