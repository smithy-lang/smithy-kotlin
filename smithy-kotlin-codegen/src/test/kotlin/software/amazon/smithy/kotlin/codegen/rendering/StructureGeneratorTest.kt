/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.RenderingContext
import software.amazon.smithy.kotlin.codegen.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.StructureShape

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructureGeneratorTest {
    // Structure generation is rather involved, instead of one giant substr search to see that everything is right we
    // look for parts of the whole as individual tests
    private val commonTestContents: String

    init {

        val model = """        
            structure Qux { }
            
            @documentation("This *is* documentation about the shape.")
            structure MyStruct {
                foo: String,
                @documentation("This *is* documentation about the member.")
                bar: PrimitiveInteger,
                baz: Integer,
                Quux: Qux,
                byteValue: Byte
            }
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#MyStruct")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        val generator = StructureGenerator(renderingCtx)
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
        commonTestContents.assertBalancedBracesAndParens()
    }

    @Test
    fun `it renders constructors`() {
        val expectedClassDecl = """
            class MyStruct private constructor(builder: BuilderImpl) {
                /**
                 * This *is* documentation about the member.
                 */
                val bar: Int = builder.bar
                val baz: Int? = builder.baz
                val byteValue: Byte? = builder.byteValue
                val foo: String? = builder.foo
                val quux: Qux? = builder.quux
        """.formatForTest(indent = "")

        commonTestContents.shouldContainOnlyOnceWithDiff(expectedClassDecl)
    }

    @Test
    fun `it renders a companion object`() {
        val expected = """
            companion object {
                @JvmStatic
                fun fluentBuilder(): FluentBuilder = BuilderImpl()

                fun builder(): DslBuilder = BuilderImpl()

                operator fun invoke(block: DslBuilder.() -> kotlin.Unit): MyStruct = BuilderImpl().apply(block).build()
                
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a toString implementation`() {
        val expected = """
            override fun toString(): kotlin.String = buildString {
                append("MyStruct(")
                append("bar=${'$'}bar,")
                append("baz=${'$'}baz,")
                append("byteValue=${'$'}byteValue,")
                append("foo=${'$'}foo,")
                append("quux=${'$'}quux)")
            }
        """.formatForTest()

        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a hashCode implementation`() {
        val expected = """
        override fun hashCode(): kotlin.Int {
            var result = bar
            result = 31 * result + (baz ?: 0)
            result = 31 * result + (byteValue?.toInt() ?: 0)
            result = 31 * result + (foo?.hashCode() ?: 0)
            result = 31 * result + (quux?.hashCode() ?: 0)
            return result
        }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders an equals implementation`() {
        val expected = """
            override fun equals(other: kotlin.Any?): kotlin.Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
        
                other as MyStruct
        
                if (bar != other.bar) return false
                if (baz != other.baz) return false
                if (byteValue != other.byteValue) return false
                if (foo != other.foo) return false
                if (quux != other.quux) return false
        
                return true
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a copy implementation`() {
        val expected = """
            fun copy(block: DslBuilder.() -> kotlin.Unit = {}): MyStruct = BuilderImpl(this).apply(block).build()
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a java builder`() {
        val expected = """
            interface FluentBuilder {
                fun build(): MyStruct
                /**
                 * This *is* documentation about the member.
                 */
                fun bar(bar: Int): FluentBuilder
                fun baz(baz: Int): FluentBuilder
                fun byteValue(byteValue: Byte): FluentBuilder
                fun foo(foo: String): FluentBuilder
                fun quux(quux: Qux): FluentBuilder
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a dsl builder`() {
        val expected = """
            interface DslBuilder {
                /**
                 * This *is* documentation about the member.
                 */
                var bar: Int
                var baz: Int?
                var byteValue: Byte?
                var foo: String?
                var quux: Qux?
        
                fun build(): MyStruct
                /**
                 * construct an [com.test.model.Qux] inside the given [block]
                 */
                fun quux(block: Qux.DslBuilder.() -> kotlin.Unit) {
                    this.quux = Qux.invoke(block)
                }
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders a builder impl`() {
        val expected = """
            private class BuilderImpl() : FluentBuilder, DslBuilder {
                override var bar: Int = 0
                override var baz: Int? = null
                override var byteValue: Byte? = null
                override var foo: String? = null
                override var quux: Qux? = null
        
                constructor(x: MyStruct) : this() {
                    this.bar = x.bar
                    this.baz = x.baz
                    this.byteValue = x.byteValue
                    this.foo = x.foo
                    this.quux = x.quux
                }
        
                override fun build(): MyStruct = MyStruct(this)
                override fun bar(bar: Int): FluentBuilder = apply { this.bar = bar }
                override fun baz(baz: Int): FluentBuilder = apply { this.baz = baz }
                override fun byteValue(byteValue: Byte): FluentBuilder = apply { this.byteValue = byteValue }
                override fun foo(foo: String): FluentBuilder = apply { this.foo = foo }
                override fun quux(quux: Qux): FluentBuilder = apply { this.quux = quux }
            }
        """.formatForTest()
        commonTestContents.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it renders class docs`() {
        commonTestContents.shouldContainOnlyOnceWithDiff("This *is* documentation about the shape.")
    }

    @Test
    fun `it renders member docs`() {
        commonTestContents.shouldContainWithDiff("This *is* documentation about the member.")
    }

    @Test
    fun `it handles shape and member docs`() {
        val model = """
            structure Foo {
                @documentation("Member documentation")
                baz: Baz,

                bar: Baz,

                qux: String
            }

            @documentation("Shape documentation")
            string Baz
        """.prependNamespaceAndService().toSmithyModel()

        /*
            The effective documentation trait of a shape is resolved using the following process:
            1. Use the documentation trait of the shape, if present.
            2. If the shape is a member, then use the documentation trait of the shape targeted by the member, if present.

            the effective documentation of Foo$baz resolves to "Member documentation", Foo$bar resolves to "Shape documentation",
            Foo$qux is not documented, Baz resolves to "Shape documentation", and Foo is not documented.
        */

        println(model.toSmithyIDL())

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#Foo")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        generated.shouldContainWithDiff("Shape documentation")
        generated.shouldContainWithDiff("Member documentation")
    }

    @Test
    fun `it handles the sensitive trait in toString`() {
        val model = """           
            @sensitive
            string Baz
            
            structure Foo {
                bar: Baz,
                @documentation("Member documentation")
                baz: Baz,
                qux: String
            }
            
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#Foo")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()

        val generated = writer.toString()
        generated.shouldContainOnlyOnceWithDiff("bar=*** Sensitive Data Redacted ***")
        generated.shouldContainOnlyOnceWithDiff("baz=*** Sensitive Data Redacted ***")
        generated.shouldContainOnlyOnceWithDiff("qux=\$qux")
    }

    @Test
    fun `it handles blob shapes`() {
        // blobs (with and without streaming) require special attention in equals() and hashCode() implementations
        val model = """
            @streaming
            blob BlobStream
            
            structure MyStruct {
                foo: Blob,
                bar: BlobStream
            }
            
        """.prependNamespaceAndService().toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val struct = model.expectShape<StructureShape>("com.test#MyStruct")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        val expectedEqualsContent = """
            override fun equals(other: kotlin.Any?): kotlin.Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false
        
                other as MyStruct
        
                if (bar != other.bar) return false
                if (foo != null) {
                    if (other.foo == null) return false
                    if (!foo.contentEquals(other.foo)) return false
                } else if (other.foo != null) return false
        
                return true
            }
        """.formatForTest()

        val expectedHashCodeContent = """
            override fun hashCode(): kotlin.Int {
                var result = bar?.hashCode() ?: 0
                result = 31 * result + (foo?.contentHashCode() ?: 0)
                return result
            }
        """.formatForTest()
        contents.shouldContainOnlyOnceWithDiff(expectedEqualsContent)
        contents.shouldContainOnlyOnceWithDiff(expectedHashCodeContent)
    }

    @Test
    fun `it generates collection types for maps with enum values`() {
        val model = """
            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                input: GetFooInput
            }
            
            @enum([
                {
                    value: "rawValue1",
                    name: "Variant1"
                },
                {
                    value: "rawValue2",
                    name: "Variant2"
                }
            ])
            string MyEnum
            
            map EnumMap {
                key: String,
                value: MyEnum
            }
            
            structure GetFooInput {
                enumMap: EnumMap
            }
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.RestJson, operations = listOf("GetFoo"), serviceName = "Example")
            .toSmithyModel()
        val struct = model.expectShape<StructureShape>("com.test#GetFooInput")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, serviceName = "Example")
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings(serviceName = "Example"))
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        listOf(
            "val enumMap: Map<String, MyEnum>? = builder.enumMap",
            "override var enumMap: Map<String, MyEnum>? = null"
        ).forEach { line ->
            contents.shouldContainOnlyOnceWithDiff(line)
        }
    }

    @Test
    fun `it generates collection types for sparse maps with enum values`() {
        val model = """
            @http(method: "POST", uri: "/input/list")
            operation GetFoo {
                input: GetFooInput
            }
            
            @enum([
                {
                    value: "rawValue1",
                    name: "Variant1"
                },
                {
                    value: "rawValue2",
                    name: "Variant2"
                }
            ])
            string MyEnum
            
            @sparse
            map EnumMap {
                key: String,
                value: MyEnum
            }
            
            structure GetFooInput {
                enumMap: EnumMap
            }
        """.prependNamespaceAndService(protocol = AwsProtocolModelDeclaration.RestJson, operations = listOf("GetFoo"), serviceName = "Example")
            .toSmithyModel()
        val struct = model.expectShape<StructureShape>("com.test#GetFooInput")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, serviceName = "Example")
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings(serviceName = "Example"))
        StructureGenerator(renderingCtx).render()
        val contents = writer.toString()

        listOf(
            "val enumMap: Map<String, MyEnum?>? = builder.enumMap",
            "override var enumMap: Map<String, MyEnum?>? = null"
        ).forEach { line ->
            contents.shouldContainOnlyOnceWithDiff(line)
        }
    }
}
