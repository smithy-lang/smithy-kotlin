/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.lang.isValidKotlinIdentifier
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.model.shapes.StringShape
import software.amazon.smithy.model.traits.EnumDefinition
import software.amazon.smithy.model.traits.EnumTrait

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
 *     abstract val value: kotlin.String
 *
 *     object Yes: SimpleYesNo() {
 *         override val value: kotlin.String = "YES"
 *         override fun toString(): kotlin.String = value
 *     }
 *
 *     object No: SimpleYesNo() {
 *         override val value: kotlin.String = "NO"
 *         override fun toString(): kotlin.String = value
 *     }
 *
 *     data class SdkUnknown(override val value: kotlin.String): SimpleYesNo() {
 *         override fun toString(): kotlin.String = value
 *     }
 *
 *     companion object {
 *
 *         fun fromValue(str: kotlin.String): SimpleYesNo = when(str) {
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
 *     abstract val value: kotlin.String
 *
 *     object Yes: TypedYesNo() {
 *         override val value: kotlin.String = "Yes"
 *         override fun toString(): kotlin.String = value
 *     }
 *
 *     object No: TypedYesNo() {
 *         override val value: kotlin.String = "No"
 *         override fun toString(): kotlin.String = value
 *     }
 *
 *     data class SdkUnknown(override val value: kotlin.String): TypedYesNo() {
 *         override fun toString(): kotlin.String = value
 *     }
 *
 *     companion object {
 *
 *         fun fromValue(str: kotlin.String): TypedYesNo = when(str) {
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

    // generated enum names must be unique, keep track of what we generate to ensure this.
    // Necessary due to prefixing and other name manipulation to create either valid identifiers
    // and idiomatic names
    private val generatedNames = mutableSetOf<String>()

    init {
        assert(shape.hasTrait<EnumTrait>())
    }

    val enumTrait: EnumTrait by lazy {
        shape.expectTrait()
    }

    fun render() {
        writer.renderDocumentation(shape)
        writer.renderAnnotations(shape)
        // NOTE: The smithy spec only allows string shapes to apply to a string shape at the moment
        writer.withBlock("public sealed class ${symbol.name} {", "}") {
            write("\npublic abstract val value: #Q\n", KotlinTypes.String)

            val sortedDefinitions = enumTrait
                .values
                .sortedBy { it.name.orElse(it.value) }

            sortedDefinitions.forEach {
                generateSealedClassVariant(it)
                write("")
            }

            if (generatedNames.contains("SdkUnknown")) throw CodegenException("generating SdkUnknown would cause duplicate variant for enum shape: $shape")

            // generate the unknown which will always be last
            writer.withBlock("public data class SdkUnknown(override val value: #Q) : #Q() {", "}", KotlinTypes.String, symbol) {
                renderToStringOverride()
            }

            write("")

            // generate the fromValue() static method
            withBlock("public companion object {", "}") {
                writer.dokka("Convert a raw value to one of the sealed variants or [SdkUnknown]")
                openBlock("public fun fromValue(str: #Q): #Q = when(str) {", KotlinTypes.String, symbol)
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
                openBlock("public fun values(): #Q<#Q> = listOf(", KotlinTypes.Collections.List, symbol)
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
        writer.write("override fun toString(): #Q = value", KotlinTypes.String)
    }

    private fun generateSealedClassVariant(definition: EnumDefinition) {
        writer.renderEnumDefinitionDocumentation(definition)
        val variantName = getVariantName(definition)
        if (!generatedNames.add(variantName)) {
            throw CodegenException("prefixing invalid enum value to form a valid Kotlin identifier causes generated sealed class names to not be unique: $variantName; shape=$shape")
        }

        writer.openBlock("public object $variantName : #Q() {", symbol)
            .write("override val value: #Q = #S", KotlinTypes.String, definition.value)
            .call { renderToStringOverride() }
            .closeBlock("}")
    }

    private fun getVariantName(definition: EnumDefinition): String {
        val identifierName = definition.variantName()

        if (!isValidKotlinIdentifier(identifierName)) {
            // prefixing didn't fix it, this must be a value since EnumDefinition.name MUST be a valid identifier
            // already, see: https://awslabs.github.io/smithy/1.0/spec/core/constraint-traits.html#enum-trait
            throw CodegenException("$identifierName is not a valid Kotlin identifier and cannot be automatically fixed with a prefix. Fix by customizing the model for $shape or giving the enum definition a name.")
        }

        return identifierName
    }
}
