/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.boxed
import software.amazon.smithy.kotlin.codegen.utils.doubleQuote
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.rulesengine.language.EndpointRuleset
import software.amazon.smithy.rulesengine.language.eval.Value
import software.amazon.smithy.rulesengine.language.lang.Identifier
import software.amazon.smithy.rulesengine.language.lang.parameters.Parameter
import software.amazon.smithy.rulesengine.language.lang.parameters.ParameterType

private const val DEFAULT_DEPRECATED_MESSAGE =
    "This field is deprecated and no longer recommended for use."

/**
 * Renders the struct of parameters to be passed to the endpoint provider for resolution.
 */
class EndpointParametersGenerator(
    private val writer: KotlinWriter,
    rules: EndpointRuleset,
) {
    private val params: List<KotlinEndpointParameter> = rules.parameters.toList().map {
        KotlinEndpointParameter(
            it.name.toKotlin(),
            it.type.toSymbol(),
            it.isRequired,
            it.defaultValue.getOrNull()?.toKotlinLiteral() ?: "null",
            it.documentation.getOrNull(),
            it.deprecated.getOrNull(),
        )
    }

    fun render() {
        renderDocumentation()
        writer.withBlock("public data class EndpointParameters(", ")") {
            params.forEach(::renderConstructorParam)
        }
        writer.withBlock("{", "}") {
            renderCompanionObject()
            write("")
            if (params.any(KotlinEndpointParameter::isRequired)) {
                renderInit()
                write("")
            }
            renderBuilder()
        }
    }

    private fun renderDocumentation() {
        writer.dokka {
            write("The set of values necessary for endpoint resolution.")
            params.forEach {
                write("@property #L #L", it.name, it.documentation)
            }
        }
    }

    private fun renderConstructorParam(param: KotlinEndpointParameter) {
        param.deprecated?.run { writeKotlinAnnotation(writer) }
        writer.write("public val #L: #P = #L,", param.name, param.type, param.defaultLiteral)
    }

    private fun renderCompanionObject() {
        writer.withBlock("public companion object {", "}") {
            write("public operator fun invoke(block: Builder.() -> Unit): EndpointParameters = Builder().apply(block).build()")
        }
    }

    private fun renderInit() {
        writer.withBlock("init {", "}") {
            params.forEach {
                if (it.isRequired) {
                    write("""requireNotNull(#1L) { "endpoint provider parameter #1L is required" }""", it.name)
                }
            }
        }
    }

    private fun renderBuilder() {
        writer.withBlock("public class Builder internal constructor() {", "}") {
            params.forEach {
                it.documentation?.let {
                    dokka { write("#L", it) }
                }
                it.deprecated?.run { writeKotlinAnnotation(writer) }
                write("public var #L: #P = #L", it.name, it.type, it.defaultLiteral)
                write("")
            }
            withBlock("public fun build(): EndpointParameters {", "}") {
                withBlock("return EndpointParameters(", ")") {
                    params.forEach {
                        it.deprecated?.run { writeInline("@Suppress(\"DEPRECATION\")") }
                        write("#L,", it.name)
                    }
                }
            }
        }
    }
}

// kotlin-mapped representation of an endpoint parameter
private data class KotlinEndpointParameter(
    val name: String,
    val type: Symbol,
    val isRequired: Boolean,
    // All endpoint params are nullable. Standard symbol rendering suppresses the default value for boxed types, but
    // endpoint params do not, so we must store the default ourselves to render.
    val defaultLiteral: String,
    val documentation: String?,
    val deprecated: Parameter.Deprecated?,
)

private fun Identifier.toKotlin(): String =
    asString().replaceFirstChar(Char::lowercase)

private fun ParameterType.toSymbol(): Symbol =
    when (this) {
        ParameterType.STRING -> KotlinTypes.String
        ParameterType.BOOLEAN -> KotlinTypes.Boolean
    }
        .toBuilder()
        .boxed()
        .build()

private fun Value.toKotlinLiteral(): String =
    when (this) {
        is Value.Str -> expectString().doubleQuote()
        is Value.Bool -> if (expectBool()) "true" else "false"
        else -> throw IllegalArgumentException("unrecognized parameter value type")
    }

private fun Parameter.Deprecated.writeKotlinAnnotation(writer: KotlinWriter) =
    writer.write("@Deprecated(#S)", message ?: DEFAULT_DEPRECATED_MESSAGE)
