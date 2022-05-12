/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.createSymbolProvider
import software.amazon.smithy.kotlin.codegen.test.prependNamespaceAndService
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.shouldContainWithDiff
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.kotlin.codegen.trimEveryLine
import software.amazon.smithy.model.shapes.UnionShape
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UnionGeneratorTest {
    @Test
    fun `it renders unions`() {
        val contents = generateUnion(
            """
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
            """
        )
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
            
                /**
                 * Casts this [MyUnion] as a [Bar] and retrieves its [kotlin.Int] value. Throws an exception if the [MyUnion] is not a
                 * [Bar].
                 */
                fun asBar(): kotlin.Int = (this as MyUnion.Bar).value
            
                /**
                 * Casts this [MyUnion] as a [Bar] and retrieves its [kotlin.Int] value. Returns null if the [MyUnion] is not a [Bar].
                 */
                fun asBarOrNull(): kotlin.Int? = (this as? MyUnion.Bar)?.value
            
                /**
                 * Casts this [MyUnion] as a [Baz] and retrieves its [kotlin.Int] value. Throws an exception if the [MyUnion] is not a
                 * [Baz].
                 */
                fun asBaz(): kotlin.Int = (this as MyUnion.Baz).value
            
                /**
                 * Casts this [MyUnion] as a [Baz] and retrieves its [kotlin.Int] value. Returns null if the [MyUnion] is not a [Baz].
                 */
                fun asBazOrNull(): kotlin.Int? = (this as? MyUnion.Baz)?.value
            
                /**
                 * Casts this [MyUnion] as a [Blz] and retrieves its [kotlin.ByteArray] value. Throws an exception if the [MyUnion] is not a
                 * [Blz].
                 */
                fun asBlz(): kotlin.ByteArray = (this as MyUnion.Blz).value
            
                /**
                 * Casts this [MyUnion] as a [Blz] and retrieves its [kotlin.ByteArray] value. Returns null if the [MyUnion] is not a [Blz].
                 */
                fun asBlzOrNull(): kotlin.ByteArray? = (this as? MyUnion.Blz)?.value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [kotlin.String] value. Throws an exception if the [MyUnion] is not a
                 * [Foo].
                 */
                fun asFoo(): kotlin.String = (this as MyUnion.Foo).value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [kotlin.String] value. Returns null if the [MyUnion] is not a [Foo].
                 */
                fun asFooOrNull(): kotlin.String? = (this as? MyUnion.Foo)?.value
            
                /**
                 * Casts this [MyUnion] as a [MyStruct] and retrieves its [test.model.MyStruct] value. Throws an exception if the [MyUnion] is not a
                 * [MyStruct].
                 */
                fun asMyStruct(): test.model.MyStruct = (this as MyUnion.MyStruct).value
            
                /**
                 * Casts this [MyUnion] as a [MyStruct] and retrieves its [test.model.MyStruct] value. Returns null if the [MyUnion] is not a [MyStruct].
                 */
                fun asMyStructOrNull(): test.model.MyStruct? = (this as? MyUnion.MyStruct)?.value
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

        assertFailsWith<IllegalStateException> {
            generator.render()
        }
    }

    @Test
    fun `it annotates deprecated unions`() {
        val contents = generateUnion(
            """
                @deprecated
                union MyUnion {
                    foo: String,
                    bar: Integer,
                }
            """
        )

        contents.shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                sealed class MyUnion {
            """.trimIndent()
        )
    }

    @Test
    fun `it annotates deprecated union members`() {
        val contents = generateUnion(
            """
                union MyUnion {
                    foo: String,

                    @deprecated
                    bar: Integer,
                }
            """
        )

        contents.trimEveryLine().shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                data class Bar(val value: kotlin.Int) : test.model.MyUnion()
            """.trimIndent()
        )
    }

    @Test
    fun `it filters event stream unions`() {
        val contents = generateUnion(
            """
                @streaming
                union MyUnion {
                    foo: MyStruct,
                    err: InvalidRequestException
                }

                structure MyStruct {
                    qux: String
                }
                
                @error("client")
                structure InvalidRequestException {
                     message: String
                }
            """
        )

        val expectedClassDecl = """
            sealed class MyUnion {
                data class Foo(val value: test.model.MyStruct) : test.model.MyUnion()
                object SdkUnknown : test.model.MyUnion()
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [test.model.MyStruct] value. Throws an exception if the [MyUnion] is not a
                 * [Foo].
                 */
                fun asFoo(): test.model.MyStruct = (this as MyUnion.Foo).value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [test.model.MyStruct] value. Returns null if the [MyUnion] is not a [Foo].
                 */
                fun asFooOrNull(): test.model.MyStruct? = (this as? MyUnion.Foo)?.value
            }
        """.trimIndent()

        contents.shouldContainWithDiff(expectedClassDecl)
    }

    private fun generateUnion(model: String): String {
        val fullModel = model.prependNamespaceAndService(namespace = "test").toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(fullModel, rootNamespace = "test")
        val writer = KotlinWriter("test")
        val union = fullModel.expectShape<UnionShape>("test#MyUnion")
        val generator = UnionGenerator(fullModel, provider, writer, union)
        generator.render()

        return writer.toString()
    }
}
