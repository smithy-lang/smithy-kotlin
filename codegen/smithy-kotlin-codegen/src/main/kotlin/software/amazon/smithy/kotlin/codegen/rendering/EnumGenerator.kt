/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.lang.isValidKotlinIdentifier
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.isStringEnumShape
import software.amazon.smithy.kotlin.codegen.utils.doubleQuote
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.shapes.IntEnumShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.traits.DocumentationTrait
import java.util.logging.Logger

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
 *         override fun toString(): kotlin.String = "Yes"
 *     }
 *
 *     object No: SimpleYesNo() {
 *         override val value: kotlin.String = "NO"
 *         override fun toString(): kotlin.String = "No"
 *     }
 *
 *     data class SdkUnknown(override val value: kotlin.String): SimpleYesNo() {
 *         override fun toString(): kotlin.String = "SdkUnknown($value)"
 *     }
 *
 *     companion object {
 *         fun fromValue(value: kotlin.String): SimpleYesNo = when (value) {
 *             "YES" -> Yes
 *             "NO" -> No
 *             else -> SdkUnknown(value)
 *         }
 *
 *         fun values(): List<SimpleYesNo> = values
 *
 *         private val values: List<SimpleYesNo> = listOf(Yes, No)
 *     }
 * }
 *
 * sealed class TypedYesNo {
 *     abstract val value: kotlin.String
 *
 *     object Yes: TypedYesNo() {
 *         override val value: kotlin.String = "Yes"
 *         override fun toString(): kotlin.String = "Yes"
 *     }
 *
 *     object No: TypedYesNo() {
 *         override val value: kotlin.String = "No"
 *         override fun toString(): kotlin.String = "No"
 *     }
 *
 *     data class SdkUnknown(override val value: kotlin.String): TypedYesNo() {
 *         override fun toString(): kotlin.String = "SdkUnknown($value)"
 *     }
 *
 *     companion object {
 *         fun fromValue(value: kotlin.String): TypedYesNo = when (value) {
 *             "Yes" -> Yes
 *             "No" -> No
 *             else -> SdkUnknown(value)
 *         }
 *
 *         fun values(): List<SimpleYesNo> = values
 *
 *         private val values: List<SimpleYesNo> = listOf(Yes, No)
 *     }
 * }
 * ```
 */
class EnumGenerator(val shape: Shape, val symbol: Symbol, val writer: KotlinWriter) {
    private val ktEnum = shape.asKotlinEnum()

    // generated enum names must be unique, keep track of what we generate to ensure this.
    // Necessary due to prefixing and other name manipulation to create either valid identifiers
    // and idiomatic names
    private val generatedNames = mutableSetOf<String>()

    fun render() {
        writer.renderDocumentation(shape)
        writer.renderAnnotations(shape)
        writer.withBlock("public sealed class #L {", "}", symbol.name) {
            write("public abstract val value: #Q", ktEnum.symbol)
            write("")

            ktEnum.variants.forEach {
                renderVariant(it)
                write("")
            }

            renderSdkUnknown()
            write("")

            renderCompanionObject()
        }
    }

    private fun renderVariant(variant: KotlinEnum.Variant) {
        variant.documentation?.let { writer.dokka(it) }
        if (!generatedNames.add(variant.name)) {
            throw CodegenException("prefixing invalid enum value to form a valid Kotlin identifier causes generated sealed class names to not be unique: ${variant.name}; shape=$shape")
        }

        writer.withBlock("public object #L : #Q() {", "}", variant.name, symbol) {
            write("override val value: #Q = #L", ktEnum.symbol, variant.valueLiteral)
            writer.write("override fun toString(): #Q = #S", KotlinTypes.String, variant.name)
        }
    }

    private fun renderSdkUnknown() {
        if (generatedNames.contains("SdkUnknown")) {
            throw CodegenException("generating SdkUnknown would cause duplicate variant for enum shape: $shape")
        }

        writer.withBlock("public data class SdkUnknown(override val value: #Q) : #Q() {", "}", ktEnum.symbol, symbol) {
            writer.write("override fun toString(): #Q = \"SdkUnknown(\$value)\"", KotlinTypes.String)
        }
    }

    private fun renderCompanionObject() {
        writer.withBlock("public companion object {", "}") {
            dokka("Convert a raw value to one of the sealed variants or [SdkUnknown]")
            withBlock("public fun fromValue(value: #Q): #Q = when (value) {", "}", ktEnum.symbol, symbol) {
                ktEnum.variants.forEach { write("#L -> #L", it.valueLiteral, it.name) }
                write("else -> SdkUnknown(value)")
            }
            write("")

            dokka("Get a list of all possible variants")
            write("public fun values(): #Q<#Q> = values", KotlinTypes.Collections.List, symbol)
            write("")

            withBlock("private val values: #Q<#Q> = listOf(", ")", KotlinTypes.Collections.List, symbol) {
                ktEnum.variants.forEach { write("#L,", it.name) }
            }
        }
    }
}

private fun Shape.asKotlinEnum(): KotlinEnum = when {
    this is IntEnumShape -> {
        val variants = members()
            .map { it to enumValues[it.memberName] }
            .sortedBy { (_, value) -> value }
            .map { (member, value) ->
                KotlinEnum.Variant(
                    member.memberName.getVariantName(),
                    value.toString(),
                    member.getTrait<DocumentationTrait>()?.value,
                )
            }
        KotlinEnum(KotlinTypes.Int, variants)
    }
    isStringEnumShape -> {
        val variants = expectTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>()
            .values
            .sortedBy { it.name.orElse(it.value) }
            .map {
                val name = it.name.orElseGet {
                    // we don't want to be doing this... name your enums, people
                    Logger.getLogger("NamingUtils").also { logger ->
                        logger.warning("Using enum value to derive generated identifier name: ${it.value}")
                    }
                    it.value
                }

                KotlinEnum.Variant(
                    name.getVariantName(),
                    it.value.doubleQuote(),
                    it.documentation.getOrNull(),
                )
            }
        KotlinEnum(KotlinTypes.String, variants)
    }
    else -> throw CodegenException("shape $this is not an enum")
}

// adapter struct to handle different enum types
private data class KotlinEnum(
    val symbol: Symbol,
    val variants: List<Variant>,
) {
    data class Variant(
        val name: String,
        val valueLiteral: String,
        val documentation: String? = null,
    )
}

private fun String.getVariantName(): String {
    val identifierName = enumVariantName()
    if (!isValidKotlinIdentifier(identifierName)) {
        // prefixing didn't fix it, this must be a value since EnumDefinition.name MUST be a valid identifier
        // already, see: https://awslabs.github.io/smithy/1.0/spec/core/constraint-traits.html#enum-trait
        throw CodegenException("$identifierName is not a valid Kotlin identifier and cannot be automatically fixed with a prefix. Fix by customizing the model or giving the enum definition a name.")
    }

    return identifierName
}
