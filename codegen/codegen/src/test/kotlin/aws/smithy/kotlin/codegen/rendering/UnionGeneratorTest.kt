/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering

import aws.smithy.kotlin.codegen.KotlinCodegenPlugin
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RenderingContext
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.test.TestModelDefault
import aws.smithy.kotlin.codegen.test.createSymbolProvider
import aws.smithy.kotlin.codegen.test.defaultSettings
import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.prependNamespaceAndService
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.shouldContainWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import aws.smithy.kotlin.codegen.trimEveryLine
import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.shapes.StructureShape
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
            """,
        )
        contents.shouldContainOnlyOnceWithDiff("package test")

        val expectedClassDecl = """
            /**
             * Documentation for MyUnion
             */
            public sealed class MyUnion {
                public data class Bar(val value: kotlin.Int) : test.model.MyUnion() {
                }
            
                public data class Baz(val value: kotlin.Int) : test.model.MyUnion() {
                }
            
                public data class Blz(val value: kotlin.ByteArray) : test.model.MyUnion() {
            
                    override fun hashCode(): kotlin.Int {
                        return value.contentHashCode()
                    }
            
                    override fun equals(other: kotlin.Any?): kotlin.Boolean {
                        if (this === other) return true
                        if (other == null || this::class != other::class) return false
            
                        other as Blz
            
                        if (!value.contentEquals(other.value)) return false
            
                        return true
                    }
                }
            
                /**
                 * Documentation for foo
                 */
                public data class Foo(val value: kotlin.String) : test.model.MyUnion() {
                }
            
                public data class MyStruct(val value: test.model.MyStruct) : test.model.MyUnion() {
                }
            
                public object SdkUnknown : test.model.MyUnion() {
                }
            
                /**
                 * Casts this [MyUnion] as a [Bar] and retrieves its [kotlin.Int] value. Throws an exception if the [MyUnion] is not a
                 * [Bar].
                 */
                public fun asBar(): kotlin.Int = (this as MyUnion.Bar).value
            
                /**
                 * Casts this [MyUnion] as a [Bar] and retrieves its [kotlin.Int] value. Returns null if the [MyUnion] is not a [Bar].
                 */
                public fun asBarOrNull(): kotlin.Int? = (this as? MyUnion.Bar)?.value
            
                /**
                 * Casts this [MyUnion] as a [Baz] and retrieves its [kotlin.Int] value. Throws an exception if the [MyUnion] is not a
                 * [Baz].
                 */
                public fun asBaz(): kotlin.Int = (this as MyUnion.Baz).value
            
                /**
                 * Casts this [MyUnion] as a [Baz] and retrieves its [kotlin.Int] value. Returns null if the [MyUnion] is not a [Baz].
                 */
                public fun asBazOrNull(): kotlin.Int? = (this as? MyUnion.Baz)?.value
            
                /**
                 * Casts this [MyUnion] as a [Blz] and retrieves its [kotlin.ByteArray] value. Throws an exception if the [MyUnion] is not a
                 * [Blz].
                 */
                public fun asBlz(): kotlin.ByteArray = (this as MyUnion.Blz).value
            
                /**
                 * Casts this [MyUnion] as a [Blz] and retrieves its [kotlin.ByteArray] value. Returns null if the [MyUnion] is not a [Blz].
                 */
                public fun asBlzOrNull(): kotlin.ByteArray? = (this as? MyUnion.Blz)?.value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [kotlin.String] value. Throws an exception if the [MyUnion] is not a
                 * [Foo].
                 */
                public fun asFoo(): kotlin.String = (this as MyUnion.Foo).value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [kotlin.String] value. Returns null if the [MyUnion] is not a [Foo].
                 */
                public fun asFooOrNull(): kotlin.String? = (this as? MyUnion.Foo)?.value
            
                /**
                 * Casts this [MyUnion] as a [MyStruct] and retrieves its [test.model.MyStruct] value. Throws an exception if the [MyUnion] is not a
                 * [MyStruct].
                 */
                public fun asMyStruct(): test.model.MyStruct = (this as MyUnion.MyStruct).value
            
                /**
                 * Casts this [MyUnion] as a [MyStruct] and retrieves its [test.model.MyStruct] value. Returns null if the [MyUnion] is not a [MyStruct].
                 */
                public fun asMyStructOrNull(): test.model.MyStruct? = (this as? MyUnion.MyStruct)?.value
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
            """,
        )

        contents.shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public sealed class MyUnion {
            """.trimIndent(),
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
            """,
        )

        contents.trimEveryLine().shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public data class Bar(val value: kotlin.Int) : test.model.MyUnion()
            """.trimIndent(),
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
            """,
        )

        val expectedClassDecl = """
            public sealed class MyUnion {
                public data class Foo(val value: test.model.MyStruct) : test.model.MyUnion() {
                }
            
                public object SdkUnknown : test.model.MyUnion() {
                }
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [test.model.MyStruct] value. Throws an exception if the [MyUnion] is not a
                 * [Foo].
                 */
                public fun asFoo(): test.model.MyStruct = (this as MyUnion.Foo).value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [test.model.MyStruct] value. Returns null if the [MyUnion] is not a [Foo].
                 */
                public fun asFooOrNull(): test.model.MyStruct? = (this as? MyUnion.Foo)?.value
            }
        """.trimIndent()

        contents.shouldContainWithDiff(expectedClassDecl)
    }

    @Test
    fun `it generates sensitive unions`() {
        val contents = generateUnion(
            """
                @sensitive
                union MyUnion {
                    foo: String,
                    bar: Integer,
                    baz: MyStruct
                }

                structure MyStruct {
                    qux: String
                }
            """,
        )

        val expectedClassDecl = """
            public sealed class MyUnion {
                public data class Bar(val value: kotlin.Int) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "Bar(*** Sensitive Data Redacted ***)"
                }
            
                public data class Baz(val value: test.model.MyStruct) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "Baz(*** Sensitive Data Redacted ***)"
                }
            
                public data class Foo(val value: kotlin.String) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "Foo(*** Sensitive Data Redacted ***)"
                }
            
                public object SdkUnknown : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SdkUnknown(*** Sensitive Data Redacted ***)"
                }
            
                /**
                 * Casts this [MyUnion] as a [Bar] and retrieves its [kotlin.Int] value. Throws an exception if the [MyUnion] is not a
                 * [Bar].
                 */
                public fun asBar(): kotlin.Int = (this as MyUnion.Bar).value
            
                /**
                 * Casts this [MyUnion] as a [Bar] and retrieves its [kotlin.Int] value. Returns null if the [MyUnion] is not a [Bar].
                 */
                public fun asBarOrNull(): kotlin.Int? = (this as? MyUnion.Bar)?.value
            
                /**
                 * Casts this [MyUnion] as a [Baz] and retrieves its [test.model.MyStruct] value. Throws an exception if the [MyUnion] is not a
                 * [Baz].
                 */
                public fun asBaz(): test.model.MyStruct = (this as MyUnion.Baz).value
            
                /**
                 * Casts this [MyUnion] as a [Baz] and retrieves its [test.model.MyStruct] value. Returns null if the [MyUnion] is not a [Baz].
                 */
                public fun asBazOrNull(): test.model.MyStruct? = (this as? MyUnion.Baz)?.value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [kotlin.String] value. Throws an exception if the [MyUnion] is not a
                 * [Foo].
                 */
                public fun asFoo(): kotlin.String = (this as MyUnion.Foo).value
            
                /**
                 * Casts this [MyUnion] as a [Foo] and retrieves its [kotlin.String] value. Returns null if the [MyUnion] is not a [Foo].
                 */
                public fun asFooOrNull(): kotlin.String? = (this as? MyUnion.Foo)?.value
            }
        """.trimIndent()

        contents.shouldContainWithDiff(expectedClassDecl)
    }

    @Test
    fun `it redacts sensitive union variants`() {
        val model = """
            @sensitive
            string SecretString

            @sensitive
            blob SecretBlob

            list SecretList {
                member: SecretString
            }

            @sensitive
            map SecretMap {
                key: String,
                value: String
            }

            map SecretValueMap {
                key: String,
                value: SecretString
            }

            map SecretKeyMap {
                key: SecretString,
                value: String
            }

            structure MyStruct {
                plain: String,
                hidden: SecretString
            }

            union Inner {
                secret: SecretString,
                plain: String
            }

            @sensitive
            union SensitiveInner {
                plain: String
            }

            union MyUnion {
                secret: SecretString,
                secretBlob: SecretBlob,
                secretList: SecretList,
                secretMap: SecretMap,
                secretValueMap: SecretValueMap,
                secretKeyMap: SecretKeyMap,
                plain: String,
                myStruct: MyStruct,
                inner: Inner,
                sensitiveInner: SensitiveInner
            }
        """
        val contents = generateUnion(model)

        val expectedVariants = """
            public sealed class MyUnion {
                public data class Inner(val value: test.model.Inner) : test.model.MyUnion() {
                }
            
                public data class MyStruct(val value: test.model.MyStruct) : test.model.MyUnion() {
                }
            
                public data class Plain(val value: kotlin.String) : test.model.MyUnion() {
                }
            
                public data class Secret(val value: kotlin.String) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "Secret(*** Sensitive Data Redacted ***)"
                }
            
                public data class SecretBlob(val value: kotlin.ByteArray) : test.model.MyUnion() {
            
                    override fun hashCode(): kotlin.Int {
                        return value.contentHashCode()
                    }
            
                    override fun equals(other: kotlin.Any?): kotlin.Boolean {
                        if (this === other) return true
                        if (other == null || this::class != other::class) return false
            
                        other as SecretBlob
            
                        if (!value.contentEquals(other.value)) return false
            
                        return true
                    }
                    override fun toString(): kotlin.String = "SecretBlob(*** Sensitive Data Redacted ***)"
                }
            
                public data class SecretKeyMap(val value: kotlin.collections.Map<kotlin.String, kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretKeyMap(*** Sensitive Data Redacted ***)"
                }
            
                public data class SecretList(val value: kotlin.collections.List<kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretList(*** Sensitive Data Redacted ***)"
                }
            
                public data class SecretMap(val value: kotlin.collections.Map<kotlin.String, kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretMap(*** Sensitive Data Redacted ***)"
                }
            
                public data class SecretValueMap(val value: kotlin.collections.Map<kotlin.String, kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretValueMap(*** Sensitive Data Redacted ***)"
                }
            
                public data class SensitiveInner(val value: test.model.SensitiveInner) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SensitiveInner(*** Sensitive Data Redacted ***)"
                }
            
                public object SdkUnknown : test.model.MyUnion() {
                }
        """.trimIndent()

        contents.shouldContainWithDiff(expectedVariants)

        // a structure variant delegates to the structure's own toString, which redacts its sensitive members
        val structContents = generateStructure(model, "test#MyStruct")
        structContents.shouldContainOnlyOnceWithDiff(
            """
                append("hidden=*** Sensitive Data Redacted ***,")
                append("plain=${'$'}plain")
            """.formatForTest(indent = "        "),
        )
    }

    private fun generateStructure(model: String, shapeId: String): String {
        val fullModel = model.prependNamespaceAndService(namespace = "test").toSmithyModel()
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(fullModel, rootNamespace = "test")
        val writer = KotlinWriter("test")
        val struct = fullModel.expectShape<StructureShape>(shapeId)
        val renderingCtx = RenderingContext(writer, struct, fullModel, provider, fullModel.defaultSettings())
        StructureGenerator(renderingCtx).render()
        return writer.toString()
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
