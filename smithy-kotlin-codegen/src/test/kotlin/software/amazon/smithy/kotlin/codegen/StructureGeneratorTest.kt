/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructureGeneratorTest {
    // Structure generation is rather involved, instead of one giant substr search to see that everything is right we
    // look for parts of the whole as individual tests
    private val commonTestContents: String

    init {

        val model = """
        namespace com.test
        
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
        
        """.asSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val struct = model.expectShape<StructureShape>("com.test#MyStruct")
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
        commonTestContents.shouldSyntacticSanityCheck()
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
"""

        commonTestContents.shouldContain(expectedClassDecl)
    }

    @Test
    fun `it renders a companion object`() {
        val expected = """
    companion object {
        @JvmStatic
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): MyStruct = BuilderImpl().apply(block).build()

    }
"""
        commonTestContents.shouldContain(expected)
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
"""
        commonTestContents.shouldContainOnlyOnce(expected)
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
"""
        commonTestContents.shouldContainOnlyOnce(expected)
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
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders a copy implementation`() {
        val expected = """
    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): MyStruct = BuilderImpl(this).apply(block).build()
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders a java builder`() {
        val expected = """
    interface Builder {
        fun build(): MyStruct
        fun bar(bar: Int): Builder
        fun baz(baz: Int): Builder
        fun byteValue(byteValue: Byte): Builder
        fun foo(foo: String): Builder
        fun quux(quux: Qux): Builder
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders a dsl builder`() {
        val expected = """
    interface DslBuilder {
        var bar: Int
        var baz: Int?
        var byteValue: Byte?
        var foo: String?
        var quux: Qux?

        fun build(): MyStruct
        fun quux(block: Qux.DslBuilder.() -> kotlin.Unit) {
            this.quux = Qux.invoke(block)
        }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders a builder impl`() {
        val expected = """
    private class BuilderImpl() : Builder, DslBuilder {
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
        override fun bar(bar: Int): Builder = apply { this.bar = bar }
        override fun baz(baz: Int): Builder = apply { this.baz = baz }
        override fun byteValue(byteValue: Byte): Builder = apply { this.byteValue = byteValue }
        override fun foo(foo: String): Builder = apply { this.foo = foo }
        override fun quux(quux: Qux): Builder = apply { this.quux = quux }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it handles enum overloads`() {
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

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        StructureGenerator(model, provider, writer, struct).render()

        val contents = writer.toString()

        val expectedDecl = """
class MyStruct private constructor(builder: BuilderImpl) {
    val foo: InstanceSize? = builder.foo
"""
        contents.shouldContainOnlyOnce(expectedDecl)

        val expectedBuilderInterface = """
    interface Builder {
        fun build(): MyStruct
        fun foo(foo: InstanceSize): Builder
    }
"""
        contents.shouldContainOnlyOnce(expectedBuilderInterface)

        val expectedDslBuilderInterface = """
    interface DslBuilder {
        var foo: InstanceSize?

        fun build(): MyStruct
    }
"""
        contents.shouldContainOnlyOnce(expectedDslBuilderInterface)

        val expectedBuilderImpl = """
    private class BuilderImpl() : Builder, DslBuilder {
        override var foo: InstanceSize? = null

        constructor(x: MyStruct) : this() {
            this.foo = x.foo
        }

        override fun build(): MyStruct = MyStruct(this)
        override fun foo(foo: InstanceSize): Builder = apply { this.foo = foo }
    }
"""
        contents.shouldContainOnlyOnce(expectedBuilderImpl)
    }

    @Test
    fun `it renders class docs`() {
        commonTestContents.shouldContainOnlyOnce("This *is* documentation about the shape.")
    }

    @Test
    fun `it renders member docs`() {
        commonTestContents.shouldContainOnlyOnce("This *is* documentation about the member.")
    }

    @Test
    fun `it handles shape and member docs`() {
        /*
        The effective documentation trait of a shape is resolved using the following process:
        1. Use the documentation trait of the shape, if present.
        2. If the shape is a member, then use the documentation trait of the shape targeted by the member, if present.

        For example, given the following model,
        structure Foo {
            @documentation("Member documentation")
            baz: Baz,

            bar: Baz,

            qux: String,
        }

        @documentation("Shape documentation")
        string Baz
        ```

        the effective documentation of Foo$baz resolves to "Member documentation", Foo$bar resolves to "Shape documentation",
        Foo$qux is not documented, Baz resolves to "Shape documentation", and Foo is not documented.

         */
        val stringShape = StringShape.builder().id("com.test#Baz").addTrait(DocumentationTrait("Shape documentation")).build()
        val member1 = MemberShape.builder().id("com.test#Foo\$bar").target("com.test#Baz").build()
        val member2 = MemberShape.builder().id("com.test#Foo\$baz").target("com.test#Baz").addTrait(DocumentationTrait("Member documentation")).build()
        val member3 = MemberShape.builder().id("com.test#Foo\$qux").target("smithy.api#String").build()

        val struct = StructureShape.builder()
            .id("com.test#Foo")
            .addMember(member1)
            .addMember(member2)
            .addMember(member3)
            .build()

        val model = Model.assembler()
            .addShapes(struct, member1, member2, member3, stringShape)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()

        val generated = writer.toString()
        generated.shouldContainOnlyOnce("Shape documentation")
        generated.shouldContainOnlyOnce("Member documentation")
    }

    @Test
    fun `it handles the sensitive trait in toString`() {
        val stringShape = StringShape.builder().id("com.test#Baz").addTrait(SensitiveTrait()).build()
        val member1 = MemberShape.builder().id("com.test#Foo\$bar").target("com.test#Baz").build()
        val member2 = MemberShape.builder().id("com.test#Foo\$baz").target("com.test#Baz").addTrait(DocumentationTrait("Member documentation")).build()
        val member3 = MemberShape.builder().id("com.test#Foo\$qux").target("smithy.api#String").build()

        val struct = StructureShape.builder()
            .id("com.test#Foo")
            .addMember(member1)
            .addMember(member2)
            .addMember(member3)
            .build()

        val model = Model.assembler()
            .addShapes(struct, member1, member2, member3, stringShape)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()

        val generated = writer.toString()
        generated.shouldContainOnlyOnce("bar=*** Sensitive Data Redacted ***")
        generated.shouldContainOnlyOnce("baz=*** Sensitive Data Redacted ***")
        generated.shouldContainOnlyOnce("qux=\$qux")
    }

    @Test
    fun `error generator throws if message property is of wrong type`() {
        val errorMessageMember =
            MemberShape.builder().id("com.test#InternalServerException\$message").target("smithy.api#Integer").build()
        val serverErrorShape = StructureShape.builder()
            .id("com.test#InternalServerException")
            .addMember(errorMessageMember)
            .addTrait(ErrorTrait("server"))
            .addTrait(RetryableTrait.builder().throttling(false).build())
            .addTrait(HttpErrorTrait(500))
            .addTrait(DocumentationTrait("Internal server error"))
            .build()

        val model = Model.assembler()
            .addShapes(serverErrorShape, errorMessageMember)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, serverErrorShape)

        val e = assertThrows<CodegenException> {
            generator.render()
        }
        e.message.shouldContain("Message is a reserved name for exception types and cannot be used for any other property")
    }

    @Test
    fun `it handles blob shapes`() {
        // blobs (with and without streaming) require special attention in equals() and hashCode() implementations
        val member1 = MemberShape.builder().id("com.test#MyStruct\$foo").target("smithy.api#Blob").build()
        val member2 = MemberShape.builder().id("com.test#MyStruct\$bar").target("com.test#BlobStream").build()

        val blobStream = BlobShape.builder().id("com.test#BlobStream").addTrait(StreamingTrait()).build()

        val struct = StructureShape.builder()
            .id("com.test#MyStruct")
            .addMember(member1)
            .addMember(member2)
            .build()

        val model = Model.assembler()
            .addShapes(struct, member1, member2, blobStream)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()
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
"""

        val expectedHashCodeContent = """
    override fun hashCode(): kotlin.Int {
        var result = bar?.hashCode() ?: 0
        result = 31 * result + (foo?.contentHashCode() ?: 0)
        return result
    }
"""
        contents.shouldContainOnlyOnce(expectedEqualsContent)
        contents.shouldContainOnlyOnce(expectedHashCodeContent)
    }

    @Test
    fun `it generates collection types for maps with enum values`() {
        val model = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

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
        """.asSmithyModel()
        val struct = model.expectShape<StructureShape>("com.test#GetFooInput")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()
        val contents = writer.toString()

        listOf(
            "val enumMap: Map<String, MyEnum>? = builder.enumMap",
            "override var enumMap: Map<String, MyEnum>? = null"
        ).forEach { line ->
            contents.shouldContainOnlyOnce(line)
        }
    }

    @Test
    fun `it generates collection types for sparse maps with enum values`() {
        val model = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

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
        """.asSmithyModel()
        val struct = model.expectShape<StructureShape>("com.test#GetFooInput")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()
        val contents = writer.toString()

        listOf(
            "val enumMap: Map<String, MyEnum?>? = builder.enumMap",
            "override var enumMap: Map<String, MyEnum?>? = null"
        ).forEach { line ->
            contents.shouldContainOnlyOnce(line)
        }
    }
}
