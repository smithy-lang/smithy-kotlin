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

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.traits.EnumConstantBody
import software.amazon.smithy.model.traits.EnumTrait

/**
 * Generates a Kotlin enum from a Smithy enum string
 *
 * For example, given the following Smithy model:
 *
 * ```
 * @enum("YES": {}, "NO": {})
 * string SimpleYesNo
 *
 * @enum("Yes": {name: "YES"}, "No": {name: "NO"})
 * string TypedYesNo
 * ```
 *
 * We will generate the following Kotlin code:
 *
 * ```
 * enum class SimpleYesNo(val value: String) {
 *     YES("YES"),
 *     NO("NO"),
 *     SDK_UNKNOWN("SDK_UNKNOWN");
 * }
 *
 * enum class TypedYesNo(val value: String) {
 *     YES("Yes"),
 *     NO("No"),
 *     SDK_UNKNOWN("SDK_UNKNOWN");
 * }
 * ```
 */
class EnumGenerator(val shape: StringShape, val symbol: Symbol, val writer: KotlinWriter) {

    init {
        assert(shape.getTrait(EnumTrait::class.java).isPresent)
    }

    val enumTrait: EnumTrait by lazy {
        shape.getTrait(EnumTrait::class.java).get()
    }

    fun render() {
        // TODO - write docs for shape
        // NOTE: The smithy spec only allows string shapes to apply to a string shape at the moment
        writer.withBlock("enum class ${symbol.name}(val value: String) {", "}") {
            enumTrait
                .values
                .entries
                .sortedBy { it.value.name.orElse(it.key) }
                .forEach {
                    generateEnumConstant(it.key, it.value)
                }

            // generate the unknown which will always be last
            writer.write("SDK_UNKNOWN(\"SDK_UNKNOWN\");\n")

            // override to string to use the enum constant value
            writer.write("override fun toString(): String = value\n")

            // generate the fromValue() static method
            withBlock("companion object {", "}") {
                writer.dokka {
                    write("Convert a raw value to an enum constant using using either the constant name or raw value")
                }
                write("fun fromValue(str: String): \$L = values().find { it.name == str || it.value == str } ?: SDK_UNKNOWN", symbol.name)
            }
        }
    }

    fun generateEnumConstant(value: String, body: EnumConstantBody) {
        // TODO - write constant documentation (body.documentation)
        val constName = body.name.orElse(value.toUpperCase())
        writer.write("$constName(\"$value\"),")
    }
}
