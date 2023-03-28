/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.AbstractConfigGenerator
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType
import software.amazon.smithy.model.Model

// FIXME - TBD where parameters are actually sourced from.
// TODO - probably refactor to not use AbstractConfigGenerator (or rename and make it more flexible since it is _close_ to what we want).

/**
 * Generate the input type used for resolving authentication schemes
 */
class AuthSchemeParametersGenerator : AbstractConfigGenerator() {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            name = "AuthSchemeParameters"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }
    }

    override val visibility: String = "internal"

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
                documentation = "The name of the operation currently being invoked."
            ).toBuilder()
                .apply {
                    propertyType = ConfigPropertyType.Required("operationName is a required auth scheme parameter")
                }.build()

            val props = listOf(operationName)
            render(codegenCtx, props, writer)
        }
    }
}