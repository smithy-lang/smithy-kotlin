/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering

import aws.smithy.kotlin.codegen.KotlinCodegenPlugin
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.model.expectShape
import aws.smithy.kotlin.codegen.test.TestModelDefault
import aws.smithy.kotlin.codegen.test.createSymbolProvider
import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.prependNamespaceAndService
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.shouldContainWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import aws.smithy.kotlin.codegen.trimEveryLine
import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.SymbolProvider
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
                    override fun toString(): kotlin.String = "MyUnion(*** Sensitive Data Redacted ***)"
                }
            
                public data class Baz(val value: test.model.MyStruct) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "MyUnion(*** Sensitive Data Redacted ***)"
                }
            
                public data class Foo(val value: kotlin.String) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "MyUnion(*** Sensitive Data Redacted ***)"
                }
            
                public object SdkUnknown : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "MyUnion(*** Sensitive Data Redacted ***)"
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
    fun `it redacts variants that target sensitive shapes`() {
        val contents = generateUnion(
            """
                @sensitive string SecretString
                @sensitive long SecretLong
                @sensitive document SecretDocument
                @sensitive structure SecretStruct { a: String }
                @sensitive map SecretMap { key: String, value: String }
                list SecretList { member: SecretString }
                list NestedSecretList { member: SecretList }
                map MapWithSecretKey { key: SecretString, value: String }
                map MapWithSecretValue { key: String, value: SecretString }
                structure StructWithSecret { name: String, password: SecretString }

                union MyUnion {
                    // redacted: the value is sensitive itself, or a collection holding sensitive data at any depth
                    secret: SecretString
                    secretLong: SecretLong
                    secretDocument: SecretDocument
                    secretStruct: SecretStruct
                    secretMap: SecretMap
                    secretList: SecretList
                    nestedSecretList: NestedSecretList
                    mapWithSecretKey: MapWithSecretKey
                    mapWithSecretValue: MapWithSecretValue

                    // kept: the structure redacts its own sensitive members, and a plain value is not secret
                    structWithSecret: StructWithSecret
                    plain: String
                }
            """,
        )

        val expectedVariants = """
            public sealed class MyUnion {
                public data class MapWithSecretKey(val value: kotlin.collections.Map<kotlin.String, kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "MapWithSecretKey(*** Sensitive Data Redacted ***)"
                }

                public data class MapWithSecretValue(val value: kotlin.collections.Map<kotlin.String, kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "MapWithSecretValue(*** Sensitive Data Redacted ***)"
                }

                public data class NestedSecretList(val value: kotlin.collections.List<kotlin.collections.List<kotlin.String>>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "NestedSecretList(*** Sensitive Data Redacted ***)"
                }

                public data class Plain(val value: kotlin.String) : test.model.MyUnion() {
                }

                public data class Secret(val value: kotlin.String) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "Secret(*** Sensitive Data Redacted ***)"
                }

                public data class SecretDocument(val value: aws.smithy.kotlin.runtime.content.Document) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretDocument(*** Sensitive Data Redacted ***)"
                }

                public data class SecretList(val value: kotlin.collections.List<kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretList(*** Sensitive Data Redacted ***)"
                }

                public data class SecretLong(val value: kotlin.Long) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretLong(*** Sensitive Data Redacted ***)"
                }

                public data class SecretMap(val value: kotlin.collections.Map<kotlin.String, kotlin.String>) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretMap(*** Sensitive Data Redacted ***)"
                }

                public data class SecretStruct(val value: test.model.SecretStruct) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "SecretStruct(*** Sensitive Data Redacted ***)"
                }

                public data class StructWithSecret(val value: test.model.StructWithSecret) : test.model.MyUnion() {
                }

                public object SdkUnknown : test.model.MyUnion() {
                }
        """.trimIndent()

        contents.shouldContainWithDiff(expectedVariants)
    }

    @Test
    fun `it redacts sensitive blob variants and keeps content equality`() {
        val contents = generateUnion(
            """
                @sensitive blob SecretBlob

                union MyUnion {
                    secret: SecretBlob
                }
            """,
        )

        val expectedVariant = """
            public data class Secret(val value: kotlin.ByteArray) : test.model.MyUnion() {

                override fun hashCode(): kotlin.Int {
                    return value.contentHashCode()
                }

                override fun equals(other: kotlin.Any?): kotlin.Boolean {
                    if (this === other) return true
                    if (other == null || this::class != other::class) return false

                    other as Secret

                    if (!value.contentEquals(other.value)) return false

                    return true
                }
                override fun toString(): kotlin.String = "Secret(*** Sensitive Data Redacted ***)"
            }
        """.formatForTest()

        contents.shouldContainOnlyOnceWithDiff(expectedVariant)
    }

    @Test
    fun `it redacts sensitive event stream variants`() {
        val contents = generateUnion(
            """
                @sensitive structure SecretEvent { payload: String }
                structure PlainEvent { payload: String }
                @error("client") structure StreamError { message: String }

                @streaming
                union MyUnion {
                    secret: SecretEvent
                    plain: PlainEvent
                    error: StreamError
                }
            """,
        )

        contents.shouldContainOnlyOnceWithDiff(
            """
                public data class Plain(val value: test.model.PlainEvent) : test.model.MyUnion() {
                }

                public data class Secret(val value: test.model.SecretEvent) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "Secret(*** Sensitive Data Redacted ***)"
                }

                public object SdkUnknown : test.model.MyUnion() {
                }
            """.formatForTest(),
        )
    }

    @Test
    fun `it redacts sensitive unions as a whole even when a variant is sensitive`() {
        val contents = generateUnion(
            """
                @sensitive string SecretString

                @sensitive
                union MyUnion {
                    secret: SecretString
                }
            """,
        )

        contents.shouldContainOnlyOnceWithDiff(
            """
                public data class Secret(val value: kotlin.String) : test.model.MyUnion() {
                    override fun toString(): kotlin.String = "MyUnion(*** Sensitive Data Redacted ***)"
                }
            """.formatForTest(),
        )
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
