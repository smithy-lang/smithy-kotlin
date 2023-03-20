/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.auth

/**
 * Responsible for providing various auth related generation components
 */
interface AuthDelegator {
    companion object {
        /**
         * Get the default auth delegator
         */
        val Default: AuthDelegator = object : AuthDelegator {
            override fun authSchemeParametersGenerator(): AuthSchemeParametersGenerator = AuthSchemeParametersGenerator()
            override fun identityProviderGenerator(): IdentityProviderConfigGenerator = IdentityProviderConfigGenerator()
            override fun authSchemeProviderGenerator(): AuthSchemeProviderGenerator = AuthSchemeProviderGenerator()
        }
    }

    /**
     * Get the type that generates the adapter for going from generated service client config to `IdentityProviderConfig`.
     */
    fun identityProviderGenerator(): IdentityProviderConfigGenerator

    /**
     * Get the type that generates the parameters that feed into the generated auth scheme resolver
     */
    fun authSchemeParametersGenerator(): AuthSchemeParametersGenerator

    /**
     * Get the type that generates the auth scheme resolver for the service client
     */
    fun authSchemeProviderGenerator(): AuthSchemeProviderGenerator
}