/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.boxed
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.utils.doubleQuote
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.language.eval.Value
import software.amazon.smithy.rulesengine.language.syntax.Identifier
import software.amazon.smithy.rulesengine.language.syntax.parameters.Parameter
import software.amazon.smithy.rulesengine.language.syntax.parameters.ParameterType

private const val DEFAULT_DEPRECATED_MESSAGE =
    "This field is deprecated and no longer recommended for use."

/**
 * Renders the struct of parameters to be passed to the endpoint provider for resolution.
 */
class EndpointParametersGenerator(
    private val writer: KotlinWriter,
    rules: EndpointRuleSet,
) {
    companion object {
        const val CLASS_NAME = "EndpointParameters"

        fun getSymbol(ctx: CodegenContext): Symbol =
            buildSymbol {
                name = CLASS_NAME
                namespace = "${ctx.settings.pkg.name}.endpoints"
            }
    }

    private val params: List<KotlinEndpointParameter> = rules.parameters.toList()
        .sortedBy { it.name.toKotlin() }
        .map {
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
        writer.withBlock("public class #L private constructor(builder: Builder) {", "}", CLASS_NAME) {
            renderFields()
            renderCompanionObject()
            write("")
            renderEquals()
            write("")
            renderHashCode()
            write("")
            renderToString()
            write("")
            renderCopy()
            write("")
            renderBuilder()
        }
    }

    private fun renderFields() {
        params.forEach {
            val initialValueExpr = if (it.isRequired) {
                """requireNotNull(builder.${it.name}) { "endpoint provider parameter #${it.name} is required" }"""
            } else {
                "builder.${it.name}"
            }

            writer.ensureSuppressDeprecation(it)
            it.renderDeclaration(writer, initialValueExpr)
            writer.write("")
        }
    }

    private fun renderDocumentation() {
        writer.dokka {
            write("The set of values necessary for endpoint resolution.")
        }
    }

    private fun renderCompanionObject() {
        writer.withBlock("public companion object {", "}") {
            write("public operator fun invoke(block: Builder.() -> Unit): #L = Builder().apply(block).build()", CLASS_NAME)
        }
    }

    private fun renderEquals() {
        writer.withBlock("public override fun equals(other: Any?): Boolean {", "}") {
            write("if (this === other) return true")
            write("if (other !is #L) return false", CLASS_NAME)
            params.forEach {
                ensureSuppressDeprecation(it)
                write("if (this.#1L != other.#1L) return false", it.name)
            }
            write("return true")
        }
    }

    private fun renderHashCode() {
        writer.withBlock("public override fun hashCode(): Int {", "}") {
            if (params.isEmpty()) {
                write("return this::class.hashCode()")
                return@withBlock
            }

            ensureSuppressDeprecation(params[0])
            write("var result = #L?.hashCode() ?: 0", params[0].name)
            params.drop(1).forEach {
                ensureSuppressDeprecation(it)
                write("result = 31 * result + (#L?.hashCode() ?: 0)", it.name)
            }
            write("return result")
        }
    }

    private fun renderToString() {
        writer.withBlock("public override fun toString(): String = buildString {", "}") {
            write("append(\"#L(\")", CLASS_NAME)
            params.forEachIndexed { index, it ->
                ensureSuppressDeprecation(it)
                write("""append("#1L=$#1L#2L")""", it.name, if (index < params.size - 1) "," else ")")
            }
        }
    }

    private fun renderCopy() {
        writer.withBlock("public fun copy(block: Builder.() -> Unit = {}): #L {", "}", CLASS_NAME) {
            withBlock("return Builder().apply {", "}") {
                params.forEach {
                    ensureSuppressDeprecation(it)
                    write("#1L = this@#2L.#1L", it.name, CLASS_NAME)
                }
                write("block()")
            }
            write(".build()")
        }
    }

    private fun renderBuilder() {
        writer.withBlock("public class Builder internal constructor() {", "}") {
            params.forEach {
                it.renderDeclaration(writer, it.defaultLiteral, isMutable = true)
            }
            write("")
            write("internal fun build(): #1L = #1L(this)", CLASS_NAME)
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

private fun KotlinEndpointParameter.renderDeclaration(writer: KotlinWriter, initialValueLiteral: String, isMutable: Boolean = false) {
    documentation?.let {
        writer.dokka { write("#L", it) }
    }
    deprecated?.run { writeKotlinAnnotation(writer) }
    writer.write("public #L #L: #P = #L", if (isMutable) "var" else "val", name, type, initialValueLiteral)
    writer.write("")
}

internal fun Identifier.toKotlin(): String =
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
        is Value.String -> expectString().doubleQuote()
        is Value.Bool -> if (expectBool()) "true" else "false"
        else -> throw IllegalArgumentException("unrecognized parameter value type")
    }

private fun Parameter.Deprecated.writeKotlinAnnotation(writer: KotlinWriter) =
    writer.write("@Deprecated(#S)", message ?: DEFAULT_DEPRECATED_MESSAGE)

private fun KotlinWriter.ensureSuppressDeprecation(param: KotlinEndpointParameter) =
    param.deprecated?.let { write("@Suppress(\"DEPRECATION\")") }
