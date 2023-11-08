/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.auth

import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigProperty
import software.amazon.smithy.kotlin.codegen.rendering.util.ConfigPropertyType

/**
 * Integration that adds the service specific auth scheme provider type to the generated service config properties
 */
class AuthSchemeProviderConfigIntegration : KotlinIntegration {
    override fun additionalServiceConfigProps(ctx: CodegenContext): List<ConfigProperty> {
        if (ctx.protocolGenerator == null) return super.additionalServiceConfigProps(ctx)
        val defaultProvider = AuthSchemeProviderGenerator.getDefaultSymbol(ctx.settings)

        return listOf(
            ConfigProperty {
                name = "authSchemeProvider"
                symbol = AuthSchemeProviderGenerator.getSymbol(ctx.settings)
                documentation = "Configure the provider used to resolve the authentication scheme to use for a particular operation."
                additionalImports = listOf(defaultProvider)
                if (ctx.settings.api.enableEndpointAuthProvider) {
                    propertyType = ConfigPropertyType.RequiredWithDefault("${defaultProvider.name}(endpointProvider)")
                } else {
                    propertyType = ConfigPropertyType.RequiredWithDefault("${defaultProvider.name}()")
                }
                // needs to come after endpointProvider
                order = 100
            },
        )
    }
}
