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
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructureGeneratorTest {
    // Structure generation is rather involved, instead of one giant substr search to see that everything is right we
    // look for parts of the whole as individual tests
    private val commonTestContents: String

    init {
        val member1 = MemberShape.builder().id("com.test#MyStruct\$foo").target("smithy.api#String").build()
        val member2 = MemberShape.builder().id("com.test#MyStruct\$bar").target("smithy.api#PrimitiveInteger").build()
        val member3 = MemberShape.builder().id("com.test#MyStruct\$baz").target("smithy.api#Integer").build()

        // struct 2 will be of type `Qux` under `MyStruct::quux` member
        val struct2 = StructureShape.builder()
            .id("com.test#Qux")
            .build()
        // structure member shape
        val member4 = MemberShape.builder().id("com.test#MyStruct\$quux").target(struct2).build()

        val struct = StructureShape.builder()
            .id("com.test#MyStruct")
            .addMember(member1)
            .addMember(member2)
            .addMember(member3)
            .addMember(member4)
            .build()

        /*
        namespace com.test
        structure Qux { }

        structure MyStruct {
            foo: String,
            bar: PrimitiveInteger,
            baz: Integer,
            quux: Qux
        }
        */
        val model = Model.assembler()
            .addShapes(struct, struct2, member1, member2, member3)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()

        commonTestContents = writer.toString()
    }
    @Test
    fun `it renders package decl`() {
        assertTrue(commonTestContents.contains("package com.test"))
    }

    @Test
    fun `it syntactic sanity checks`() {
        // sanity check since we are testing fragments
        var openBraces = 0
        var closedBraces = 0
        var openParens = 0
        var closedParens = 0
        commonTestContents.forEach {
            when (it) {
                '{' -> openBraces++
                '}' -> closedBraces++
                '(' -> openParens++
                ')' -> closedParens++
            }
        }
        assertEquals(openBraces, closedBraces)
        assertEquals(openParens, closedParens)
    }

    @Test
    fun `it renders constructors`() {
        val expectedClassDecl = """
class MyStruct private constructor(builder: BuilderImpl) {
    val bar: Integer = builder.bar
    val baz: Integer? = builder.baz
    val foo: String? = builder.foo
    val quux: Qux? = builder.quux
"""

        commonTestContents.shouldContain(expectedClassDecl)
    }

    @Test
    fun `it renders a companion object`() {
        val expected = """
    companion object {
        @JvmStatic
        fun builder(): Builder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> Unit): MyStruct = BuilderImpl().apply(block).build()

    }
"""
        commonTestContents.shouldContain(expected)
    }

    @Test
    fun `it renders a copy implementation`() {
        val expected = """
    fun copy(
        bar: Integer = this.bar,
        baz: Integer? = this.baz,
        foo: String? = this.foo,
        quux: Qux? = this.quux
    ): MyStruct = BuilderImpl(this).apply {
        this.bar = bar
        this.baz = baz
        this.foo = foo
        this.quux = quux
    }.build()
"""
        commonTestContents.shouldContain(expected)
    }

    @Test
    fun `it renders a java builder`() {
        val expected = """
    interface Builder {
        fun build(): MyStruct
        fun bar(bar: Integer): Builder
        fun baz(baz: Integer): Builder
        fun foo(foo: String): Builder
        fun quux(quux: Qux): Builder
    }
"""
        commonTestContents.shouldContain(expected)
    }

    @Test
    fun `it renders a dsl builder`() {
        val expected = """
    interface DslBuilder {
        var bar: Integer
        var baz: Integer?
        var foo: String?
        var quux: Qux?

        fun quux(block: Qux.DslBuilder.() -> Unit) {
            this.quux = Qux.invoke(block)
        }
    }
"""
        commonTestContents.shouldContain(expected)
    }

    @Test
    fun `it renders a builder impl`() {
        val expected = """
    private class BuilderImpl() : Builder, DslBuilder {
        override var bar: Integer = 0
        override var baz: Integer? = null
        override var foo: String? = null
        override var quux: Qux? = null

        constructor(x: MyStruct) : this() {
            this.bar = x.bar
            this.baz = x.baz
            this.foo = x.foo
            this.quux = x.quux
        }

        override fun build(): MyStruct = MyStruct(this)
        override fun bar(bar: Integer): Builder = apply { this.bar = bar }
        override fun baz(baz: Integer): Builder = apply { this.baz = baz }
        override fun foo(foo: String): Builder = apply { this.foo = foo }
        override fun quux(quux: Qux): Builder = apply { this.quux = quux }
    }
"""
        commonTestContents.shouldContain(expected)
    }

    @Test
    fun `it handles enum overloads`() {
        // enums are backed by strings internally to provide forwards compatibility
        val trait = EnumTrait.builder()
            .addEnum(EnumDefinition.builder().value("t2.nano").name("T2_NANO").build())
            .addEnum(EnumDefinition.builder().value("t2.micro").name("T2_MICRO").build())
            .build()

        val enumShape = StringShape.builder()
            .id("com.test#InstanceSize")
            .addTrait(trait)
            .build()

        val member1 = MemberShape.builder().id("com.test#MyStruct\$foo").target(enumShape).build()

        val struct = StructureShape.builder()
            .id("com.test#MyStruct")
            .addMember(member1)
            .build()

        /*
        namespace com.test

        @enum("t2.nano": {name: "T2_NANO"}, "t2.micro": {name: "T2_MICRO"})
        String InstanceSize

        structure MyStruct {
            foo: InstanceSize,
        }
        */
        val model = Model.assembler()
            .addShapes(struct, member1, enumShape)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        StructureGenerator(model, provider, writer, struct).render()

        val contents = writer.toString()

        val expectedDecl = """
class MyStruct private constructor(builder: BuilderImpl) {
    val fooAsString: String? = builder.fooAsString
    val foo: InstanceSize?
        get() = fooAsString?.let { InstanceSize.fromValue(it) }
"""
        contents.shouldContain(expectedDecl)

        val expectedBuilderInterface = """
    interface Builder {
        fun build(): MyStruct
        fun foo(foo: InstanceSize): Builder
        fun foo(foo: String)
    }
"""
        contents.shouldContain(expectedBuilderInterface)

        val expectedDslBuilderInterface = """
    interface DslBuilder {
        var foo: InstanceSize?

        fun foo(foo: String)
    }
"""
        contents.shouldContain(expectedDslBuilderInterface)

        val expectedBuilderImpl = """
    private class BuilderImpl() : Builder, DslBuilder {
        var fooAsString: String? = null
        override var foo: InstanceSize? = null
            set(value) {
                fooAsString = value.toString()
                field = value
            }

        constructor(x: MyStruct) : this() {
            this.fooAsString = x.fooAsString
        }

        override fun build(): MyStruct = MyStruct(this)
        override fun foo(foo: InstanceSize): Builder = apply { this.fooAsString = foo.toString() }
        override fun foo(foo: String) { this.fooAsString = foo }
    }
"""
        contents.shouldContain(expectedBuilderImpl)
    }
}
