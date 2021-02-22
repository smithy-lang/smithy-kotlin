/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.shapes.UnionShape

class UnionGeneratorTest {
    @Test
    fun `it renders unions`() {
        val model = """
        namespace com.test
        
        @documentation("Documentation for MyUnion")
        union MyUnion {
            @documentation("Documentation for foo")
            foo: String,
            bar: PrimitiveInteger,
            baz: Integer,
            blz: Blob,
            myStruct: MyStruct
        }
        
        structure MyStruct {
            qux: String
        }
            
        """.asSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val union = model.expectShape<UnionShape>("com.test#MyUnion")
        val generator = UnionGenerator(model, provider, writer, union)
        generator.render()

        val contents = writer.toString()
        assertTrue(contents.contains("package com.test"))

        val expectedClassDecl = """
/**
 * Documentation for MyUnion
 */
sealed class MyUnion {
    data class Bar(val value: kotlin.Int) : test.model.MyUnion()
    data class Baz(val value: kotlin.Int) : test.model.MyUnion()
    data class Blz(val value: kotlin.ByteArray) : test.model.MyUnion() {

        override fun hashCode(): kotlin.Int {
            return value.contentHashCode()
        }

        override fun equals(other: kotlin.Any?): kotlin.Boolean {
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
    data class Foo(val value: kotlin.String) : test.model.MyUnion()
    data class MyStruct(val value: test.model.MyStruct) : test.model.MyUnion()
}
"""

        contents.shouldContain(expectedClassDecl)
    }
}
