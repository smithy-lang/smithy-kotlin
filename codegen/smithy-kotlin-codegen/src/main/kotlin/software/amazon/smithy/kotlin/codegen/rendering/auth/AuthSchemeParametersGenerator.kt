/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.clientName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.asNullable
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParametersGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.AbstractConfigGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.model.Model

/**
 * Generate the input type used for resolving authentication schemes
 */
class AuthSchemeParametersGenerator : AbstractConfigGenerator() {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            val prefix = clientName(settings.sdkId)
            name = "${prefix}AuthSchemeParameters"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }
    }

    fun render(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = getSymbol(ctx.settings)

        ctx.delegator.useSymbolWriter(symbol) { writer ->
            writer.putContext("configClass.name", symbol.name)

            val codegenCtx = object : CodegenContext {
                override val model: Model = ctx.model
                override val protocolGenerator: ProtocolGenerator? = null
                override val settings: KotlinSettings = ctx.settings
                override val symbolProvider: SymbolProvider = ctx.symbolProvider
                override val integrations: List<KotlinIntegration> = ctx.integrations
            }

            val operationName = ConfigProperty.String(
                "operationName",
                documentation = "The name of the operation currently being invoked.",
            ).toBuilder()
                .apply {
                    propertyType = ConfigPropertyType.Required("operationName is a required auth scheme parameter")
                }.build()

            val endpointParamsProperty = ConfigProperty {
                name = "endpointParameters"
                this.symbol = EndpointParametersGenerator.getSymbol(ctx.settings).asNullable()
                documentation = """
                    The parameters used for endpoint resolution. The default implementation of this interface 
                    relies on endpoint metadata to resolve auth scheme candidates.
                """.trimIndent()
            }.takeIf { ctx.settings.api.enableEndpointAuthProvider }

            val props = listOfNotNull(operationName, endpointParamsProperty)
            render(codegenCtx, props, writer)
        }
    }

    override fun renderAdditionalMethods(ctx: CodegenContext, props: List<ConfigProperty>, writer: KotlinWriter) {
        writer.write("")
        renderToString(ctx, props, writer)
        writer.write("")
        renderEquals(ctx, props, writer)
        writer.write("")
        renderHashCode(props, writer)
        writer.write("")
        renderCopy(ctx, props, writer)
    }

    private fun renderEquals(ctx: CodegenContext, props: List<ConfigProperty>, writer: KotlinWriter) {
        writer.withBlock("override fun equals(other: Any?): Boolean {", "}") {
            write("if (this === other) return true")
            write("if (other !is #T) return false", getSymbol(ctx.settings))
            props.forEach { prop ->
                write("if (this.#1L != other.#1L) return false", prop.propertyName)
            }
            write("return true")
        }
    }
    private fun renderToString(ctx: CodegenContext, props: List<ConfigProperty>, writer: KotlinWriter) {
        writer.withBlock("override fun toString(): String = buildString {", "}") {
            write("append(\"#L(\")", getSymbol(ctx.settings).name)
            props.forEachIndexed { idx, prop ->
                write("""append("#1L=$#1L#2L")""", prop.propertyName, if (idx < props.size - 1) "," else ")")
            }
        }
    }

    private fun renderHashCode(props: List<ConfigProperty>, writer: KotlinWriter) {
        writer.withBlock("override fun hashCode(): Int {", "}") {
            if (props.isEmpty()) {
                write("return this::class.hashCode()")
                return@withBlock
            }

            write("var result = #L?.hashCode() ?: 0", props[0].propertyName)
            props.drop(1).forEach {
                write("result = 31 * result + (#L?.hashCode() ?: 0)", it.propertyName)
            }
            write("return result")
        }
    }

    private fun renderCopy(ctx: CodegenContext, props: List<ConfigProperty>, writer: KotlinWriter) {
        val symbol = getSymbol(ctx.settings)
        writer.withBlock("#visibility:L fun copy(block: Builder.() -> Unit = {}): #T {", "}", symbol) {
            withBlock("return Builder().apply {", "}") {
                props.forEach {
                    write("#1L = this@#2L.#1L", it.propertyName, symbol.name)
                }
                write("block()")
            }
            write(".build()")
        }
    }
}
