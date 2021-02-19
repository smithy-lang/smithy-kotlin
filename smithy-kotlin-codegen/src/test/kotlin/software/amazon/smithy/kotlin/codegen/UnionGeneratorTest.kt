/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.shapes.UnionShape
import java.lang.IllegalStateException

class UnionGeneratorTest {
    @Test
    fun `it renders unions`() {
        val model = """
            namespace com.test

            structure MyStruct {
                qux: String,
            }

            /// Documentation for MyUnion
            union MyUnion {
                /// Documentation for foo
                foo: String,
                bar: PrimitiveInteger,
                baz: Integer,
                blz: Blob,
                MyStruct: MyStruct,
            }
        """.asSmithyModel()
        val union = model.expectShape<UnionShape>("com.test#MyUnion")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
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
                data class SdkUnknown(val value: kotlin.String) : MyUnion() {
                    override fun toString(): kotlin.String = value
                }
            }
        """.trimIndent()

        contents.shouldContainOnlyOnceWithDiff(expectedClassDecl)
    }

    @Test
    fun `it fails to generate unions with colliding member names`() {
        val model = """
            namespace com.test

            structure MyStruct {
                qux: String,
            }
           
            union MyUnion {                
                sdkUnknown: String
            }
        """.asSmithyModel()
        val union = model.expectShape<UnionShape>("com.test#MyUnion")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")
        val generator = UnionGenerator(model, provider, writer, union)

        Assertions.assertThrows(IllegalStateException::class.java) {
            generator.render()
        }
    }
}
