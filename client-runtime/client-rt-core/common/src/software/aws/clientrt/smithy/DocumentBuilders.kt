/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.smithy

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
    fun n(number: Number): SmithyNumber =
        SmithyNumber(number)
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
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] as [SmithyNumber] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Number) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] as [SmithyString] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: String) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Document) {
        require(content[this] == null) { "Key $this is already registered in builder" }
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
