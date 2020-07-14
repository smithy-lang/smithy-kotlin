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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait

class EnumGeneratorTest {

    @Test
    fun `it generates unnamed enums`() {
        val trait = EnumTrait.builder()
            .addEnum(EnumDefinition.builder().value("FOO").build())
            .addEnum(EnumDefinition.builder().value("BAR").documentation("Documentation for bar").build())
            .build()

        val shape = StringShape.builder()
            .id("com.test#Baz")
            .addTrait(trait)
            .addTrait(DocumentationTrait("Documentation for this enum"))
            .build()

        val model = Model.assembler()
            .addShapes(shape)
            .assemble()
            .unwrap()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter("com.test")
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        val expectedEnumDecl = """
/**
 * Documentation for this enum
 */
enum class Baz(val value: String) {
    /**
     * Documentation for bar
     */
    BAR("BAR"),
    FOO("FOO"),
    SDK_UNKNOWN("SDK_UNKNOWN");

    override fun toString(): String = value

    companion object {
        /**
         * Convert a raw value to an enum constant using using either the constant name or raw value
         */
        fun fromValue(str: String): Baz = values().find { it.name == str || it.value == str } ?: SDK_UNKNOWN
    }
}
"""

        contents.shouldContainOnlyOnce(expectedEnumDecl)
    }

    @Test
    fun `it generates named enums`() {
        val trait = EnumTrait.builder()
            .addEnum(EnumDefinition.builder().value("t2.nano").name("T2_NANO").build())
            .addEnum(EnumDefinition.builder().value("t2.micro").name("T2_MICRO").documentation("\"\"\"\n" +
                    "T2 instances are Burstable Performance\n" +
                    "Instances that provide a baseline level of CPU\n" +
                    "performance with the ability to burst above the\n" +
                    "baseline.\"\"\"").build())
            .build()

        val shape = StringShape.builder()
            .id("com.test#Baz")
            .addTrait(trait)
            .addTrait(DocumentationTrait("Documentation for this enum"))
            .build()

        val model = Model.assembler()
            .addShapes(shape)
            .assemble()
            .unwrap()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter("com.test")
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        val expectedEnumDecl = """
/**
 * Documentation for this enum
 */
enum class Baz(val value: String) {
    /**
     * ${"\"\"\""}
     * T2 instances are Burstable Performance
     * Instances that provide a baseline level of CPU
     * performance with the ability to burst above the
     * baseline.${"\"\"\""}
     */
    T2_MICRO("t2.micro"),
    T2_NANO("t2.nano"),
    SDK_UNKNOWN("SDK_UNKNOWN");

    override fun toString(): String = value

    companion object {
        /**
         * Convert a raw value to an enum constant using using either the constant name or raw value
         */
        fun fromValue(str: String): Baz = values().find { it.name == str || it.value == str } ?: SDK_UNKNOWN
    }
}
"""
        contents.shouldContainOnlyOnce(expectedEnumDecl)
    }

    @Test
    fun `it prefixes invalid kotlin identifier names`() {
        // (smithy) enum names are required to start with an ascii letter or underscore, values are not.
        // when an enum is unnamed we convert the value to the name. This test ensures those names are escaped
        // appropriately when the value itself would constitute an invalid kotlin identifier name

        val trait = EnumTrait.builder()
            .addEnum(EnumDefinition.builder().value("0").build())
            .addEnum(EnumDefinition.builder().value("foo").build())
            .build()
        /*
        @enum([
            {
                value: "0"
            },
            {
                value: "foo"
            }
        ])
        string Baz
        */

        val shape = StringShape.builder()
            .id("com.test#Baz")
            .addTrait(trait)
            .build()

        val model = Model.assembler()
            .addShapes(shape)
            .assemble()
            .unwrap()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter("com.test")
        EnumGenerator(shape, symbol, writer).render()
        val contents = writer.toString()

        val expectedEnumDecl = """
enum class Baz(val value: String) {
    _0("0"),
    FOO("foo"),
    SDK_UNKNOWN("SDK_UNKNOWN");

    override fun toString(): String = value

    companion object {
        /**
         * Convert a raw value to an enum constant using using either the constant name or raw value
         */
        fun fromValue(str: String): Baz = values().find { it.name == str || it.value == str } ?: SDK_UNKNOWN
    }
}
"""

        contents.shouldContainOnlyOnce(expectedEnumDecl)
    }

    @Test
    fun `it fails if prefixing causes a conflict`() {
        // names and values are required to be unique, prefixing invalid identifiers with '_' could potentially
        // (albeit unlikely) cause a conflict with an existing name

        val trait = EnumTrait.builder()
            .addEnum(EnumDefinition.builder().value("0").build())
            .addEnum(EnumDefinition.builder().value("_0").build())
            .build()
        /*
        @enum([
            {
                value: "0"
            },
            {
                value: "_0"
            }
        ])
        string Baz
        */

        val shape = StringShape.builder()
            .id("com.test#Baz")
            .addTrait(trait)
            .build()

        val model = Model.assembler()
            .addShapes(shape)
            .assemble()
            .unwrap()

        val provider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val symbol = provider.toSymbol(shape)
        val writer = KotlinWriter("com.test")
        val ex = assertThrows<CodegenException> {
            EnumGenerator(shape, symbol, writer).render()
        }

        ex.message!!.shouldContain("prefixing invalid enum value to form a valid Kotlin identifier causes generated enum names to not be unique")
    }
}
