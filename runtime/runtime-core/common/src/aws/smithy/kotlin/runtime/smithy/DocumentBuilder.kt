/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.smithy

import kotlin.jvm.JvmName

public class DocumentBuilder internal constructor() {
    public val content: MutableMap<String, Document?> = linkedMapOf()

    public infix fun String.to(value: Number?) {
        require(this !in content) { "Key $this is already registered in builder" }
        content[this] = if (value != null) Document(value) else null
    }

    public infix fun String.to(value: String?) {
        require(this !in content) { "Key $this is already registered in builder" }
        content[this] = if (value != null) Document(value) else null
    }

    public infix fun String.to(value: Boolean?) {
        require(this !in content) { "Key $this is already registered in builder" }
        content[this] = if (value != null) Document(value) else null
    }

    public infix fun String.to(value: Document?) {
        require(this !in content) { "Key $this is already registered in builder" }
        content[this] = value
    }

    public infix fun String.to(@Suppress("UNUSED_PARAMETER") value: Nothing?) {
        require(this !in content) { "Key $this is already registered in builder" }
        content[this] = null
    }

    public class ListBuilder internal constructor() {
        public val content: MutableList<Document?> = mutableListOf()

        public fun add(value: Number?): Boolean =
            content.add(if (value != null) Document(value) else null)
        public fun add(value: String?): Boolean =
            content.add(if (value != null) Document(value) else null)
        public fun add(value: Boolean?): Boolean =
            content.add(if (value != null) Document(value) else null)
        public fun add(value: Document?): Boolean =
            content.add(value)
        public fun add(@Suppress("UNUSED_PARAMETER") value: Nothing?): Boolean =
            content.add(null)

        @JvmName("addAllNumbers") public fun addAll(value: List<Number?>): Unit = value.forEach(::add)
        @JvmName("addAllStrings") public fun addAll(value: List<String?>): Unit = value.forEach(::add)
        @JvmName("addAllBooleans") public fun addAll(value: List<Boolean?>): Unit = value.forEach(::add)
        @JvmName("addAllDocuments") public fun addAll(value: List<Document?>): Unit = value.forEach(::add)
    }

    /**
     * Builds a [Document] list with the given builder.
     */
    public fun buildList(init: ListBuilder.() -> Unit): Document =
        ListBuilder().let {
            it.init()
            Document(it.content)
        }
}

/**
 * Builds a [Document] with the given builder.
 */
public fun buildDocument(init: DocumentBuilder.() -> Unit): Document =
    DocumentBuilder().let {
        it.init()
        Document(it.content)
    }
