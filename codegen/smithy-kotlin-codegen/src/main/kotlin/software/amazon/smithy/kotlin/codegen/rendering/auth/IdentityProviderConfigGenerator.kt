/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.clientName
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.knowledge.AuthIndex
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Generate the adapter for going from generated service client config to `IdentityProviderConfig`
 * used by the operation auth handler when resolving identity provider from an auth scheme.
 */
class IdentityProviderConfigGenerator {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            val prefix = clientName(settings.sdkId)
            name = "${prefix}IdentityProviderConfigAdapter"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }
    }

    fun render(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = getSymbol(ctx.settings)
        val serviceSymbol = ctx.symbolProvider.toSymbol(ctx.service)
        ctx.delegator.useSymbolWriter(symbol) { writer ->
            writer.withBlock(
                "internal class #T (private val config: #T.Config): #T {",
                "}",
                symbol,
                serviceSymbol,
                RuntimeTypes.Auth.Identity.IdentityProviderConfig,
            ) {
                renderImpl(ctx, writer)
            }
        }
    }

    private fun renderImpl(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {
        val authIndex = AuthIndex()
        val authSchemes = authIndex.authHandlersForService(ctx)
        writer.write("")
            .withBlock(
                "override fun identityProviderForScheme(schemeId: #T): #T = when(schemeId.id) {",
                "}",
                RuntimeTypes.Auth.Identity.AuthSchemeId,
                RuntimeTypes.Auth.Identity.IdentityProvider,
            ) {
                authSchemes.forEach {
                    writeInline("#S -> ", it.authSchemeId)
                    it.identityProviderAdapterExpression(this)
                }
                write("else -> error(#S)", "auth scheme \$schemeId not configured for client")
            }
            .write("")
    }
}
