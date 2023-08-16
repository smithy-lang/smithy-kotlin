/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Generates the adapter from the service type specific auth scheme provider and the generic one used to execute
 * a request.
 */
class AuthSchemeProviderAdapterGenerator {
    companion object {
        fun getSymbol(settings: KotlinSettings): Symbol = buildSymbol {
            name = "AuthSchemeProviderAdapter"
            namespace = "${settings.pkg.name}.auth"
            definitionFile = "$name.kt"
        }
    }

    fun render(ctx: ProtocolGenerator.GenerationContext) {
        val symbol = getSymbol(ctx.settings)
        ctx.delegator.useSymbolWriter(symbol) { writer ->
            // TODO - auth parameters will need bound per/request as applicable (e.g. like EP2.0 or generate one per/request).
            //        This is a simplified version (using object) while design is in flux.
            writer.withBlock("internal object #T: #T {", "}", symbol, RuntimeTypes.HttpClient.Operation.AuthSchemeResolver) {
                withBlock(
                    "override suspend fun resolve(request: #T): List<#T> {",
                    "}",
                    RuntimeTypes.HttpClient.Operation.SdkHttpRequest,
                    RuntimeTypes.Auth.Identity.AuthOption,
                ) {
                    withBlock("val params = #T {", "}", AuthSchemeParametersGenerator.getSymbol(ctx.settings)) {
                        addImport(RuntimeTypes.Core.Utils.get)
                        write("operationName = request.context[#T.OperationName]", RuntimeTypes.SmithyClient.SdkClientOption)
                    }

                    write("return #T.resolveAuthScheme(params)", AuthSchemeProviderGenerator.getDefaultSymbol(ctx.settings))
                }
            }
        }
    }
}
