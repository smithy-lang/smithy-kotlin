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
        val member4 = MemberShape.builder().id("com.test#MyStruct\$qux").target("smithy.api#String").build()

        val struct = StructureShape.builder()
                .id("com.test#MyStruct")
                .addMember(member4)
                .build()
        val union = UnionShape.builder()
                .id("com.test#MyUnion")
                .addMember(member1)
                .addMember(member2)
                .addMember(member3)
                .addMember(struct.defaultName(), struct.id)
                .addTrait(DocumentationTrait("Documentation for MyUnion"))
                .build()
        val model = Model.assembler()
                .addShapes(union, struct, member1, member2, member3, member4)
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
    data class MyStruct(val myStruct: MyStruct) : MyUnion()
    data class Bar(val bar: Integer) : MyUnion()
    data class Baz(val baz: Integer) : MyUnion()
    /**
     * Documentation for foo
     */
    data class Foo(val foo: String) : MyUnion()
}
"""

        contents.shouldContain(expectedClassDecl)
    }
}
