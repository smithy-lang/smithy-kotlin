/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy

import kotlin.jvm.JvmName

class DocumentBuilder internal constructor() {
    val content: MutableMap<String, Document> = linkedMapOf()

    infix fun String.to(value: Number?) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = if (value != null) Document(value) else Document.Null
    }

    infix fun String.to(value: String?) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = if (value != null) Document(value) else Document.Null
    }

    infix fun String.to(value: Boolean?) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = if (value != null) Document(value) else Document.Null
    }

    infix fun String.to(value: Document?) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = value ?: Document.Null
    }

    infix fun String.to(value: Nothing?) {
        require(content[this] == null) { "Key $this is already registered in builder" }
        content[this] = Document.Null
    }

    class ListBuilder internal constructor() {
        val content: MutableList<Document> = mutableListOf()

        fun add(value: Number?): Boolean =
            content.add(if (value != null) Document(value) else Document.Null)
        fun add(value: String?): Boolean =
            content.add(if (value != null) Document(value) else Document.Null)
        fun add(value: Boolean?): Boolean =
            content.add(if (value != null) Document(value) else Document.Null)
        fun add(value: Document?): Boolean =
            content.add(value ?: Document.Null)
        fun add(value: Nothing?): Boolean =
            content.add(Document.Null)

        @JvmName("addAllNumbers") fun addAll(value: List<Number?>) = value.forEach(::add)
        @JvmName("addAllStrings") fun addAll(value: List<String?>) = value.forEach(::add)
        @JvmName("addAllBooleans") fun addAll(value: List<Boolean?>) = value.forEach(::add)
        @JvmName("addAllDocuments") fun addAll(value: List<Document?>) = value.forEach(::add)
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
