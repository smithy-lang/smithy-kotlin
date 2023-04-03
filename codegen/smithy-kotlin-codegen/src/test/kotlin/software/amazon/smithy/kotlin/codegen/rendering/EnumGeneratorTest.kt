/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldContainOnlyOnce
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.StringShape
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EnumGeneratorTest {

    @Test
    fun `it generates unnamed string enums`() {
        val model = """
            @enum([
                {
                    value: "FOO"
                },
                {
                    value: "BAR",
                    documentation: "Documentation for bar"
                }
            ])
            @documentation("Documentation for this enum")
            string Baz
            
        """.prependNamespaceAndService(namespace = "test").toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "test")
        val shape = model.expectShape<StringShape>("test#Baz")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        val expectedEnumDecl = """
/**
 * Documentation for this enum
 */
public sealed class Baz {
    public abstract val value: kotlin.String

    /**
     * Documentation for bar
     */
    public object Bar : test.model.Baz() {
        override val value: kotlin.String = "BAR"
        override fun toString(): kotlin.String = value
    }

    public object Foo : test.model.Baz() {
        override val value: kotlin.String = "FOO"
        override fun toString(): kotlin.String = value
    }

    public data class SdkUnknown(override val value: kotlin.String) : test.model.Baz() {
        override fun toString(): kotlin.String = value
    }

    public companion object {
        /**
         * Convert a raw value to one of the sealed variants or [SdkUnknown]
         */
        public fun fromValue(v: kotlin.String): test.model.Baz = when (v) {
            "BAR" -> Bar
            "FOO" -> Foo
            else -> SdkUnknown(v)
        }

        /**
         * Get a list of all possible variants
         */
        public fun values(): kotlin.collections.List<test.model.Baz> = listOf(
            Bar,
            Foo,
        )
    }
}
"""

        contents.shouldContainOnlyOnceWithDiff(expectedEnumDecl)
    }

    @Test
    fun `it generates named string enums`() {
        val t2MicroDoc = "T2 instances are Burstable Performance\n" +
            "Instances that provide a baseline level of CPU\n" +
            "performance with the ability to burst above the\n" +
            "baseline."

        val model = """            
            @enum([
                {
                    value: "t2.nano",
                    name: "T2_NANO"
                },
                {
                    value: "t2.micro",
                    name: "T2_MICRO",
                    documentation: "$t2MicroDoc"
                }
            ])
            @documentation("Documentation for this enum")
            string Baz            
        """.prependNamespaceAndService(namespace = "test")
            .toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "test")
        val shape = model.expectShape<StringShape>("test#Baz")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        val expectedEnumDecl = """
/**
 * Documentation for this enum
 */
public sealed class Baz {
    public abstract val value: kotlin.String

    /**
     * T2 instances are Burstable Performance
     * Instances that provide a baseline level of CPU
     * performance with the ability to burst above the
     * baseline.
     */
    public object T2Micro : test.model.Baz() {
        override val value: kotlin.String = "t2.micro"
        override fun toString(): kotlin.String = value
    }

    public object T2Nano : test.model.Baz() {
        override val value: kotlin.String = "t2.nano"
        override fun toString(): kotlin.String = value
    }

    public data class SdkUnknown(override val value: kotlin.String) : test.model.Baz() {
        override fun toString(): kotlin.String = value
    }

    public companion object {
        /**
         * Convert a raw value to one of the sealed variants or [SdkUnknown]
         */
        public fun fromValue(v: kotlin.String): test.model.Baz = when (v) {
            "t2.micro" -> T2Micro
            "t2.nano" -> T2Nano
            else -> SdkUnknown(v)
        }

        /**
         * Get a list of all possible variants
         */
        public fun values(): kotlin.collections.List<test.model.Baz> = listOf(
            T2Micro,
            T2Nano,
        )
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedEnumDecl)
    }

    @Test
    fun `it generates int enums`() {
        val model = """
            @documentation("Documentation for this enum")
            intEnum Baz {
                T2_NANO = 2

                X9_OMEGA = 9999

                @documentation("Documentation for this value")
                T2_MICRO = 1
            }
        """.prependNamespaceAndService(version = "2", namespace = "test")
            .toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "test")
        val shape = model.expectShape<IntEnumShape>("test#Baz")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        val expectedEnumDecl = """
/**
 * Documentation for this enum
 */
public sealed class Baz {
    public abstract val value: kotlin.Int

    /**
     * Documentation for this value
     */
    public object T2Micro : test.model.Baz() {
        override val value: kotlin.Int = 1
        override fun toString(): kotlin.String = value.toString()
    }

    public object T2Nano : test.model.Baz() {
        override val value: kotlin.Int = 2
        override fun toString(): kotlin.String = value.toString()
    }

    public object X9Omega : test.model.Baz() {
        override val value: kotlin.Int = 9999
        override fun toString(): kotlin.String = value.toString()
    }

    public data class SdkUnknown(override val value: kotlin.Int) : test.model.Baz() {
        override fun toString(): kotlin.String = value.toString()
    }

    public companion object {
        /**
         * Convert a raw value to one of the sealed variants or [SdkUnknown]
         */
        public fun fromValue(v: kotlin.Int): test.model.Baz = when (v) {
            1 -> T2Micro
            2 -> T2Nano
            9999 -> X9Omega
            else -> SdkUnknown(v)
        }

        /**
         * Get a list of all possible variants
         */
        public fun values(): kotlin.collections.List<test.model.Baz> = listOf(
            T2Micro,
            T2Nano,
            X9Omega,
        )
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedEnumDecl)
    }

    @Test
    fun `it prefixes invalid kotlin identifier names`() {
        // (smithy) enum names are required to start with an ascii letter or underscore, values are not.
        // when an enum is unnamed we convert the value to the name. This test ensures those names are escaped
        // appropriately when the value itself would constitute an invalid kotlin identifier name

        val model = """
            @enum([
                {
                    value: "0"
                },
                {
                    value: "foo"
                }
            ])
            string Baz
            
        """.prependNamespaceAndService(namespace = "test").toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "test")
        val shape = model.expectShape<StringShape>("test#Baz")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        contents.shouldContainOnlyOnce("object _0 : test.model.Baz()")
    }

    @Test
    fun `it annotates deprecated shapes`() {
        val model = """
            @deprecated
            @enum([
                {
                    value: "apple"
                },
                {
                    value: "banana"
                }
            ])
            string Fruit
        """.prependNamespaceAndService(namespace = "test").toSmithyModel()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "test")
        val shape = model.expectShape<StringShape>("test#Fruit")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        contents.shouldContainOnlyOnce(
            """
                @Deprecated("No longer recommended for use. See AWS API documentation for more details.")
                public sealed class Fruit {
            """.trimIndent(),
        )
    }

    @Test
    fun `it fails if prefixing causes a conflict`() {
        // names and values are required to be unique, prefixing invalid identifiers with '_' could potentially
        // (albeit unlikely) cause a conflict with an existing name

        val model = """           
            @enum([
                {
                    value: "0"
                },
                {
                    value: "_0"
                }
            ])
            string Baz
            
        """.prependNamespaceAndService().toSmithyModel()

        val shape = model.expectShape<StringShape>("com.test#Baz")

        val provider = KotlinCodegenPlugin.createSymbolProvider(model)
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val ex = assertFailsWith<CodegenException> {
            EnumGenerator(shape, symbol, writer).render()
        }

        ex.message!!.shouldContain("prefixing invalid enum value to form a valid Kotlin identifier causes generated sealed class names to not be unique")
    }
}
