/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
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

        fun getImplementationSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            val prefix = clientName(settings.sdkId)
            name = "${prefix}AuthSchemeParametersImpl"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }
    }

    override val visibility: String = "internal"

    fun render(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = getSymbol(ctx.settings)
        val implSymbol = getImplementationSymbol(ctx.settings)

        ctx.delegator.useSymbolWriter(symbol) { writer ->
            writer.withBlock(
                "#L interface #T {",
                "}",
                ctx.settings.api.visibility,
                symbol,
            ) {
                dokka("The name of the operation currently being invoked.")
                write("public val operationName: String")
                if (ctx.settings.api.enableEndpointAuthProvider) {
                    dokka(
                        """
                        |The parameters used for endpoint resolution. The default implementation of this interface 
                        |relies on endpoint metadata to resolve auth scheme candidates.
                        |
                        """.trimMargin(),
                    )
                    write("public val endpointParameters: #P", EndpointParametersGenerator.getSymbol(ctx.settings).asNullable())
                }
            }
        }

        ctx.delegator.useSymbolWriter(implSymbol) { writer ->
            writer.putContext("configClass.name", implSymbol.name)

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
                    baseClass = symbol
                }.build()

            val endpointParamsProperty = ConfigProperty {
                name = "endpointParameters"
                this.symbol = EndpointParametersGenerator.getSymbol(ctx.settings).asNullable()
                baseClass = symbol
                documentation = """
                    The parameters used for endpoint resolution. The default implementation of this interface 
                    relies on endpoint metadata to resolve auth scheme candidates.
                """.trimIndent()
            }.takeIf { ctx.settings.api.enableEndpointAuthProvider }

            val props = listOfNotNull(operationName, endpointParamsProperty)
            render(codegenCtx, props, writer)
        }
    }
}
