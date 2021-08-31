/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

// Code generated by smithy-kotlin-codegen. DO NOT EDIT!

package aws.smithy.kotlin.serde.benchmarks.model.twitter

class Media private constructor(builder: BuilderImpl) {
    val displayUrl: String? = builder.displayUrl
    val expandedUrl: String? = builder.expandedUrl
    val id: Long? = builder.id
    val idStr: String? = builder.idStr
    val indices: List<Int>? = builder.indices
    val mediaUrl: String? = builder.mediaUrl
    val mediaUrlHttps: String? = builder.mediaUrlHttps
    val sizes: Sizes? = builder.sizes
    val type: String? = builder.type
    val url: String? = builder.url

    companion object {
        @JvmStatic
        fun fluentBuilder(): FluentBuilder = BuilderImpl()

        fun builder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): Media = BuilderImpl().apply(block).build()

    }

    override fun toString(): kotlin.String = buildString {
        append("Media(")
        append("displayUrl=$displayUrl,")
        append("expandedUrl=$expandedUrl,")
        append("id=$id,")
        append("idStr=$idStr,")
        append("indices=$indices,")
        append("mediaUrl=$mediaUrl,")
        append("mediaUrlHttps=$mediaUrlHttps,")
        append("sizes=$sizes,")
        append("type=$type,")
        append("url=$url)")
    }

    override fun hashCode(): kotlin.Int {
        var result = displayUrl?.hashCode() ?: 0
        result = 31 * result + (expandedUrl?.hashCode() ?: 0)
        result = 31 * result + (id?.hashCode() ?: 0)
        result = 31 * result + (idStr?.hashCode() ?: 0)
        result = 31 * result + (indices?.hashCode() ?: 0)
        result = 31 * result + (mediaUrl?.hashCode() ?: 0)
        result = 31 * result + (mediaUrlHttps?.hashCode() ?: 0)
        result = 31 * result + (sizes?.hashCode() ?: 0)
        result = 31 * result + (type?.hashCode() ?: 0)
        result = 31 * result + (url?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Media

        if (displayUrl != other.displayUrl) return false
        if (expandedUrl != other.expandedUrl) return false
        if (id != other.id) return false
        if (idStr != other.idStr) return false
        if (indices != other.indices) return false
        if (mediaUrl != other.mediaUrl) return false
        if (mediaUrlHttps != other.mediaUrlHttps) return false
        if (sizes != other.sizes) return false
        if (type != other.type) return false
        if (url != other.url) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): Media = BuilderImpl(this).apply(block).build()

    interface FluentBuilder {
        fun build(): Media
        fun displayUrl(displayUrl: String): FluentBuilder
        fun expandedUrl(expandedUrl: String): FluentBuilder
        fun id(id: Long): FluentBuilder
        fun idStr(idStr: String): FluentBuilder
        fun indices(indices: List<Int>): FluentBuilder
        fun mediaUrl(mediaUrl: String): FluentBuilder
        fun mediaUrlHttps(mediaUrlHttps: String): FluentBuilder
        fun sizes(sizes: Sizes): FluentBuilder
        fun type(type: String): FluentBuilder
        fun url(url: String): FluentBuilder
    }

    interface DslBuilder {
        var displayUrl: String?
        var expandedUrl: String?
        var id: Long?
        var idStr: String?
        var indices: List<Int>?
        var mediaUrl: String?
        var mediaUrlHttps: String?
        var sizes: Sizes?
        var type: String?
        var url: String?

        fun build(): Media
        /**
         * construct an [aws.smithy.kotlin.serde.benchmarks.model.Sizes] inside the given [block]
         */
        fun sizes(block: Sizes.DslBuilder.() -> kotlin.Unit) {
            this.sizes = Sizes.invoke(block)
        }
    }

    private class BuilderImpl() : FluentBuilder, DslBuilder {
        override var displayUrl: String? = null
        override var expandedUrl: String? = null
        override var id: Long? = null
        override var idStr: String? = null
        override var indices: List<Int>? = null
        override var mediaUrl: String? = null
        override var mediaUrlHttps: String? = null
        override var sizes: Sizes? = null
        override var type: String? = null
        override var url: String? = null

        constructor(x: Media) : this() {
            this.displayUrl = x.displayUrl
            this.expandedUrl = x.expandedUrl
            this.id = x.id
            this.idStr = x.idStr
            this.indices = x.indices
            this.mediaUrl = x.mediaUrl
            this.mediaUrlHttps = x.mediaUrlHttps
            this.sizes = x.sizes
            this.type = x.type
            this.url = x.url
        }

        override fun build(): Media = Media(this)
        override fun displayUrl(displayUrl: String): FluentBuilder = apply { this.displayUrl = displayUrl }
        override fun expandedUrl(expandedUrl: String): FluentBuilder = apply { this.expandedUrl = expandedUrl }
        override fun id(id: Long): FluentBuilder = apply { this.id = id }
        override fun idStr(idStr: String): FluentBuilder = apply { this.idStr = idStr }
        override fun indices(indices: List<Int>): FluentBuilder = apply { this.indices = indices }
        override fun mediaUrl(mediaUrl: String): FluentBuilder = apply { this.mediaUrl = mediaUrl }
        override fun mediaUrlHttps(mediaUrlHttps: String): FluentBuilder = apply { this.mediaUrlHttps = mediaUrlHttps }
        override fun sizes(sizes: Sizes): FluentBuilder = apply { this.sizes = sizes }
        override fun type(type: String): FluentBuilder = apply { this.type = type }
        override fun url(url: String): FluentBuilder = apply { this.url = url }
    }
}
