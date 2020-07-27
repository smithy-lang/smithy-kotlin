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

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.utils.CaseUtils

/**
 * Generates a Kotlin sealed class from a Smithy enum string
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
 * sealed class SimpleYesNo {
 *     abstract val value: String
 *
 *     object Yes: SimpleYesNo() {
 *         override val value: String = "YES"
 *         override fun toString(): String = value
 *     }
 *
 *     object No: SimpleYesNo() {
 *         override val value: String = "NO"
 *         override fun toString(): String = value
 *     }
 *
 *     data class SdkUnknown(override val value: String): SimpleYesNo() {
 *         override fun toString(): String = value
 *     }
 *
 *     companion object {
 *
 *         fun fromValue(str: String): SimpleYesNo = when(str) {
 *             "YES" -> Yes
 *             "NO" -> No
 *             else -> SdkUnknown(str)
 *         }
 *
 *         fun values(): List<SimpleYesNo> = listOf(Yes, No)
 *     }
 * }
 *
 * sealed class TypedYesNo {
 *     abstract val value: String
 *
 *     object Yes: TypedYesNo() {
 *         override val value: String = "Yes"
 *         override fun toString(): String = value
 *     }
 *
 *     object No: TypedYesNo() {
 *         override val value: String = "No"
 *         override fun toString(): String = value
 *     }
 *
 *     data class SdkUnknown(override val value: String): TypedYesNo() {
 *         override fun toString(): String = value
 *     }
 *
 *     companion object {
 *
 *         fun fromValue(str: String): TypedYesNo = when(str) {
 *             "Yes" -> Yes
 *             "No" -> No
 *             else -> SdkUnknown(str)
 *         }
 *
 *         fun values(): List<TypedYesNo> = listOf(Yes, No)
 *     }
 * }
 * ```
 */
class EnumGenerator(val shape: StringShape, val symbol: Symbol, val writer: KotlinWriter) {

    // generated enum names must be unique, keep track of what we generate to ensure this (necessary due to prefixing)
    private val generatedNames = mutableSetOf<String>()

    init {
        assert(shape.getTrait(EnumTrait::class.java).isPresent)
    }

    val enumTrait: EnumTrait by lazy {
        shape.getTrait(EnumTrait::class.java).get()
    }

    fun render() {
        writer.renderDocumentation(shape)
        // NOTE: The smithy spec only allows string shapes to apply to a string shape at the moment
        writer.withBlock("sealed class ${symbol.name} {", "}") {
            write("\nabstract val value: String\n")

            val sortedDefinitions = enumTrait
                .values
                .sortedBy { it.name.orElse(it.value) }

                sortedDefinitions.forEach {
                    generateSealedClassVariant(it)
                    write("")
                }

            if (generatedNames.contains("SdkUnknown")) throw CodegenException("generating SdkUnknown would cause duplicate variant for enum shape: $shape")

            // generate the unknown which will always be last
            writer.withBlock("data class SdkUnknown(override val value: String) : ${symbol.name}() {", "}") {
                renderToStringOverride()
            }

            write("")

            // generate the fromValue() static method
            withBlock("companion object {", "}") {
                writer.dokka("Convert a raw value to one of the sealed variants or [SdkUnknown]")
                openBlock("fun fromValue(str: String): \$L = when(str) {", symbol.name)
                    .call {
                        sortedDefinitions.forEach { definition ->
                            val variantName = getVariantName(definition)
                                write("\"${definition.value}\" -> $variantName")
                        }
                    }
                    .write("else -> SdkUnknown(str)")
                    .closeBlock("}")
                    .write("")

                writer.dokka("Get a list of all possible variants")
                openBlock("fun values(): List<\$L> = listOf(", symbol.name)
                    .call {
                        sortedDefinitions.forEachIndexed { idx, definition ->
                            val variantName = getVariantName(definition)
                            val suffix = if (idx < sortedDefinitions.size - 1) "," else ""
                            write("${variantName}$suffix")
                        }
                    }
                    .closeBlock(")")
            }
        }
    }

    private fun renderToStringOverride() {
        // override to string to use the enum constant value
        writer.write("override fun toString(): String = value")
    }

    private fun generateSealedClassVariant(definition: EnumDefinition) {
        writer.renderEnumDefinitionDocumentation(definition)
        val variantName = getVariantName(definition)
        if (!generatedNames.add(variantName)) {
            throw CodegenException("prefixing invalid enum value to form a valid Kotlin identifier causes generated sealed class names to not be unique: $variantName; shape=$shape")
        }

        writer.openBlock("object $variantName : ${symbol.name}() {")
            .write("override val value: String = \"${definition.value}\"")
            .call { renderToStringOverride() }
            .closeBlock("}")
    }

    private fun getVariantName(definition: EnumDefinition): String {
        val raw = definition.name.orElseGet {
            CaseUtils.toSnakeCase(definition.value).replace(".", "_")
        }

        val identifierName = CaseUtils.toCamelCase(raw, true, '_')

        return if (!isValidKotlinIdentifier(identifierName)) {
            "_$identifierName"
        } else {
            identifierName
        }
    }
}
