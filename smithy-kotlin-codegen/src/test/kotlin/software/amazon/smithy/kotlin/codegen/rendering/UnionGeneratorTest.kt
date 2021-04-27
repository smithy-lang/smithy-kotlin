/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.ext.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.UnionShape
import java.lang.IllegalStateException

class UnionGeneratorTest {
    @Test
    fun `it renders unions`() {
        val model = """
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
            
        """.prependNamespaceAndService(namespace = "test").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "test")
        val writer = KotlinWriter("test")
        val union = model.expectShape<UnionShape>("test#MyUnion")
        val generator = UnionGenerator(model, provider, writer, union)
        generator.render()

        val contents = writer.toString()
        contents.shouldContainOnlyOnceWithDiff("package test")

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
                object SdkUnknown : test.model.MyUnion()
            }
        """.trimIndent()

        contents.shouldContainWithDiff(expectedClassDecl)
    }

    @Test
    fun `it fails to generate unions with colliding member names`() {
        val model = """
            structure MyStruct {
                qux: String,
            }
           
            union MyUnion {                
                sdkUnknown: String
            }
        """.prependNamespaceAndService().toSmithyModel()
        val union = model.expectShape<UnionShape>("com.test#MyUnion")

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val generator = UnionGenerator(model, provider, writer, union)

        Assertions.assertThrows(IllegalStateException::class.java) {
            generator.render()
        }
    }
}
