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
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.traits.EnumConstantBody
import software.amazon.smithy.model.traits.EnumTrait

class EnumGeneratorTest {

    @Test
    fun `generates unnamed enums`() {
        val trait = EnumTrait.builder()
            .addEnum("FOO", EnumConstantBody.builder().build())
            .addEnum("BAR", EnumConstantBody.builder().build())
            .build()

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

        contents.shouldContain(expectedEnumDecl)
    }

    @Test
    fun `generates named enums`() {
        val trait = EnumTrait.builder()
            .addEnum("t2.nano", EnumConstantBody.builder().name("T2_NANO").build())
            .addEnum("t2.micro", EnumConstantBody.builder().name("T2_MICRO").build())
            .build()

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
        contents.shouldContain(expectedEnumDecl)
    }
}
