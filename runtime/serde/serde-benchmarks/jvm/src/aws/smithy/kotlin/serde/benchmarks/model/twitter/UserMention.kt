/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

// Code generated by smithy-kotlin-codegen. DO NOT EDIT!

package aws.smithy.kotlin.serde.benchmarks.model.twitter



class UserMention private constructor(builder: BuilderImpl) {
    val id: Int? = builder.id
    val idStr: String? = builder.idStr
    val indices: List<Int>? = builder.indices
    val name: String? = builder.name
    val screenName: String? = builder.screenName

    companion object {
        @JvmStatic
        fun fluentBuilder(): FluentBuilder = BuilderImpl()

        fun builder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): UserMention = BuilderImpl().apply(block).build()

    }

    override fun toString(): kotlin.String = buildString {
        append("UserMention(")
        append("id=$id,")
        append("idStr=$idStr,")
        append("indices=$indices,")
        append("name=$name,")
        append("screenName=$screenName)")
    }

    override fun hashCode(): kotlin.Int {
        var result = id ?: 0
        result = 31 * result + (idStr?.hashCode() ?: 0)
        result = 31 * result + (indices?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + (screenName?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserMention

        if (id != other.id) return false
        if (idStr != other.idStr) return false
        if (indices != other.indices) return false
        if (name != other.name) return false
        if (screenName != other.screenName) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): UserMention = BuilderImpl(this).apply(block).build()

    interface FluentBuilder {
        fun build(): UserMention
        fun id(id: Int): FluentBuilder
        fun idStr(idStr: String): FluentBuilder
        fun indices(indices: List<Int>): FluentBuilder
        fun name(name: String): FluentBuilder
        fun screenName(screenName: String): FluentBuilder
    }

    interface DslBuilder {
        var id: Int?
        var idStr: String?
        var indices: List<Int>?
        var name: String?
        var screenName: String?

        fun build(): UserMention
    }

    private class BuilderImpl() : FluentBuilder, DslBuilder {
        override var id: Int? = null
        override var idStr: String? = null
        override var indices: List<Int>? = null
        override var name: String? = null
        override var screenName: String? = null

        constructor(x: UserMention) : this() {
            this.id = x.id
            this.idStr = x.idStr
            this.indices = x.indices
            this.name = x.name
            this.screenName = x.screenName
        }

        override fun build(): UserMention = UserMention(this)
        override fun id(id: Int): FluentBuilder = apply { this.id = id }
        override fun idStr(idStr: String): FluentBuilder = apply { this.idStr = idStr }
        override fun indices(indices: List<Int>): FluentBuilder = apply { this.indices = indices }
        override fun name(name: String): FluentBuilder = apply { this.name = name }
        override fun screenName(screenName: String): FluentBuilder = apply { this.screenName = screenName }
    }
}
