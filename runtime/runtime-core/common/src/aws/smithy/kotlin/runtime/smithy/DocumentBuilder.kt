/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy

class DocumentBuilder internal constructor() {
    val content: MutableMap<String, Document> = linkedMapOf()

    infix fun String.to(value: Number) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = Document(value)
    }

    infix fun String.to(value: String) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = Document(value)
    }

    infix fun String.to(value: Boolean) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = Document(value)
    }

    infix fun String.to(value: Document?) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = value ?: Document.Null
    }

    class ListBuilder internal constructor() {
        val content: MutableList<Document> = mutableListOf()

        fun add(value: Number) {
            content.add(Document(value))
        }

        fun add(value: String) {
            content.add(Document(value))
        }

        fun add(value: Boolean) {
            content.add(Document(value))
        }

        fun add(value: Document?) {
            content.add(value ?: Document.Null)
        }
    }

    /**
     * Builds a [Document] list with the given builder.
     */
    fun buildList(init: ListBuilder.() -> Unit): Document =
        ListBuilder().let {
            it.init()
            Document(it.content)
        }
}

/**
 * Builds a [Document] with the given builder.
 */
fun buildDocument(init: DocumentBuilder.() -> Unit): Document =
    DocumentBuilder().let {
        it.init()
        Document(it.content)
    }
