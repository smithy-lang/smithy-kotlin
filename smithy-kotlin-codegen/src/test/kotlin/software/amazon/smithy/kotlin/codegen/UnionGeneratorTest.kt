/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.DocumentationTrait

class UnionGeneratorTest {
    @Test
    fun `it renders unions`() {
        val member1 = MemberShape.builder().id("com.test#MyUnion\$foo").target("smithy.api#String").addTrait(DocumentationTrait("Documentation for foo")).build()
        val member2 = MemberShape.builder().id("com.test#MyUnion\$bar").target("smithy.api#PrimitiveInteger").build()
        val member3 = MemberShape.builder().id("com.test#MyUnion\$baz").target("smithy.api#Integer").build()
        val member4 = MemberShape.builder().id("com.test#MyUnion\$blz").target("smithy.api#Blob").build()
        val member5 = MemberShape.builder().id("com.test#MyStruct\$qux").target("smithy.api#String").build()

        val struct = StructureShape.builder()
            .id("com.test#MyStruct")
            .addMember(member5)
            .build()
        val union = UnionShape.builder()
            .id("com.test#MyUnion")
            .addMember(member1)
            .addMember(member2)
            .addMember(member3)
            .addMember(member4)
            .addMember(struct.defaultName(), struct.id)
            .addTrait(DocumentationTrait("Documentation for MyUnion"))
            .build()
        val model = Model.assembler()
            .addShapes(union, struct, member1, member2, member3, member5)
            .assemble()
            .unwrap()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val writer = KotlinWriter("com.test")
        val generator = UnionGenerator(model, provider, writer, union)
        generator.render()

        val contents = writer.toString()
        assertTrue(contents.contains("package com.test"))

        val expectedClassDecl = """
/**
 * Documentation for MyUnion
 */
sealed class MyUnion {
    data class MyStruct(val value: MyStruct) : MyUnion()
    data class Bar(val value: Int) : MyUnion()
    data class Baz(val value: Int) : MyUnion()
    data class Blz(val value: ByteArray) : MyUnion() {

        override fun hashCode(): Int {
            return value.contentHashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Blz

            if (!value.contentEquals(other.value)) return false

            return true
        }
    }
    /**
     * Documentation for foo
     */
    data class Foo(val value: String) : MyUnion()
}
"""

        contents.shouldContain(expectedClassDecl)
    }
}
