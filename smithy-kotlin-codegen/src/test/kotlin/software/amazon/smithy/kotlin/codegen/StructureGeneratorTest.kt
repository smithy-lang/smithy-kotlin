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
import io.kotest.matchers.string.shouldContainOnlyOnce
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ListShape
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StructureGeneratorTest {
    // Structure generation is rather involved, instead of one giant substr search to see that everything is right we
    // look for parts of the whole as individual tests
    private val commonTestContents: String
    private val clientErrorTestContents: String
    private val serverErrorTestContents: String

    init {
        val member1 = MemberShape.builder().id("com.test#MyStruct\$foo").target("smithy.api#String").build()
        val member2 = MemberShape.builder().id("com.test#MyStruct\$bar").target("smithy.api#PrimitiveInteger").addTrait(DocumentationTrait("This *is* documentation about the member.")).build()
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
            .addTrait(DocumentationTrait("This *is* documentation about the shape."))
            .build()

        /*
        @httpError(400)
        @error("client")
        @documentation("Input fails to satisfy the constraints specified by the service")
        structure ValidationException {
            @documentation("""
                Enumerated reason why the input was invalid
            """)
            @required
            reason: ValidationExceptionReason,
            @documentation("""
                List of specific input fields which were invalid
            """)
            @required
            fieldList: ValidationExceptionFieldList
        }
        */
        val trait = EnumTrait.builder()
                .addEnum(EnumDefinition.builder().value("Reason1").name("REASON1").build())
                .addEnum(EnumDefinition.builder().value("Reason2").name("REASON2").build())
                .build()
        val enumShape = StringShape.builder()
                .id("com.error.test#ValidationExceptionReason")
                .addTrait(trait)
                .build()
        val errorMember1 = MemberShape.builder()
                .id("com.error.test#ValidationException\$reason")
                .addTrait(DocumentationTrait("Enumerated reason why the input was invalid"))
                .target(enumShape)
                .build()
        val mem = StructureShape.builder().id("com.error.test#ValidationExceptionField").build()
        val listMember = MemberShape.builder().id("com.error.test#ValidationExceptionFieldList\$member").target(mem).build()
        val list = ListShape.builder()
                .id("com.error.test#ValidationExceptionFieldList")
                .member(listMember)
                .build()
        val errorMember2 = MemberShape.builder()
                .id("com.error.test#ValidationException\$fieldList")
                .addTrait(DocumentationTrait("List of specific input fields which were invalid"))
                .target(list)
                .build()
        val clientErrorShape = StructureShape.builder()
                .id("com.error.test#ValidationException")
                .addMember(errorMember1)
                .addMember(errorMember2)
                .addTrait(ErrorTrait("client"))
                .addTrait(HttpErrorTrait(400))
                .addTrait(DocumentationTrait("Input fails to satisfy the constraints specified by the service"))
                .build()

        /*
        @httpError(500)
        @error("server")
        @retryable
        @documentation("Internal server error")
        structure InternalServerException {
            @required
            message: String
        }
        */
        val errorMessageMember = MemberShape.builder().id("com.test#InternalServerException\$message").target("smithy.api#String").build()
        val serverErrorShape = StructureShape.builder()
                .id("com.test#InternalServerException")
                .addMember(errorMessageMember)
                .addTrait(ErrorTrait("server"))
                .addTrait(RetryableTrait.builder().throttling(false).build())
                .addTrait(HttpErrorTrait(500))
                .addTrait(DocumentationTrait("Internal server error"))
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

        val errorModel = Model.assembler()
                .addShapes(clientErrorShape, errorMember1, errorMember2, listMember, enumShape, list, mem)
                .assemble()
                .unwrap()

        val errorProvider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(errorModel, "error.test")
        val errorWriter = KotlinWriter("com.error.test")
        val clientErrorGenerator = StructureGenerator(errorModel, errorProvider, errorWriter, clientErrorShape)
        clientErrorGenerator.render()

        clientErrorTestContents = errorWriter.toString()

        val serverErrorGenerator = StructureGenerator(model, provider, writer, serverErrorShape)
        serverErrorGenerator.render()

        serverErrorTestContents = writer.toString()
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
    /**
     * This *is* documentation about the member.
     */
    val bar: Int = builder.bar
    val baz: Int? = builder.baz
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

        operator fun invoke(block: DslBuilder.() -> Unit): MyStruct = BuilderImpl().apply(block).build()

    }
"""
        commonTestContents.shouldContain(expected)
    }

    @Test
    fun `it renders a toString implementation`() {
        val expected = """
    override fun toString() = buildString {
        append("MyStruct(")
        append("bar=${'$'}bar,")
        append("baz=${'$'}baz,")
        append("foo=${'$'}foo,")
        append("quux=${'$'}quux)")
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
    }

    @Test
    fun `it renders a hashCode implementation`() {
        val expected = """
    override fun hashCode(): Int {
        var result = bar
        result = 31 * result + (baz ?: 0)
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
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MyStruct

        if (bar != other.bar) return false
        if (baz != other.baz) return false
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
    fun copy(block: DslBuilder.() -> Unit = {}): MyStruct = BuilderImpl(this).apply(block).build()
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
        var foo: String?
        var quux: Qux?

        fun quux(block: Qux.DslBuilder.() -> Unit) {
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
        override var foo: String? = null
        override var quux: Qux? = null

        constructor(x: MyStruct) : this() {
            this.bar = x.bar
            this.baz = x.baz
            this.foo = x.foo
            this.quux = x.quux
        }

        override fun build(): MyStruct = MyStruct(this)
        override fun bar(bar: Int): Builder = apply { this.bar = bar }
        override fun baz(baz: Int): Builder = apply { this.baz = baz }
        override fun foo(foo: String): Builder = apply { this.foo = foo }
        override fun quux(quux: Qux): Builder = apply { this.quux = quux }
    }
"""
        commonTestContents.shouldContainOnlyOnce(expected)
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

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
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

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, struct)
        generator.render()

        val generated = writer.toString()
        generated.shouldContainOnlyOnce("bar=*** Sensitive Data Redacted ***")
        generated.shouldContainOnlyOnce("baz=*** Sensitive Data Redacted ***")
        generated.shouldContainOnlyOnce("qux=\$qux")
    }

    @Test
    fun `error generator extends correctly`() {
        val expectedClientClassDecl = """
class ValidationException private constructor(builder: BuilderImpl) : ServiceException() {
"""

        clientErrorTestContents.shouldContain(expectedClientClassDecl)

        val expectedServerClassDecl = """
class InternalServerException private constructor(builder: BuilderImpl) : ServiceException() {
"""

        serverErrorTestContents.shouldContain(expectedServerClassDecl)
    }

    @Test
    fun `error generator sets error type correctly`() {
        val expectedClientClassDecl = "override val errorType = ErrorType.Client"

        clientErrorTestContents.shouldContain(expectedClientClassDecl)

        val expectedServerClassDecl = "override val errorType = ErrorType.Server"

        serverErrorTestContents.shouldContain(expectedServerClassDecl)
    }

    @Test
    fun `error generator syntactic sanity checks`() {
        // sanity check since we are testing fragments
        var openBraces = 0
        var closedBraces = 0
        var openParens = 0
        var closedParens = 0
        clientErrorTestContents.forEach {
            when (it) {
                '{' -> openBraces++
                '}' -> closedBraces++
                '(' -> openParens++
                ')' -> closedParens++
            }
        }
        assertEquals(openBraces, closedBraces)
        assertEquals(openParens, closedParens)
        serverErrorTestContents.forEach {
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
    fun `error generator renders override with message member`() {
        val expectedConstr = """
    override val message: String = builder.message!!
"""

        serverErrorTestContents.shouldContain(expectedConstr)

        clientErrorTestContents.shouldNotContain(expectedConstr)
    }

    @Test
    fun `error generator renders isRetryable`() {
        val expectedConstr = """
        override val isRetryable = true
"""

        serverErrorTestContents.shouldContain(expectedConstr)

        clientErrorTestContents.shouldNotContain(expectedConstr)
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

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val generator = StructureGenerator(model, provider, writer, serverErrorShape)

        val e = assertThrows<CodegenException> {
            generator.render()
        }
        e.message.shouldContain("Message is a reserved name for exception types and cannot be used for any other property")
    }
}
