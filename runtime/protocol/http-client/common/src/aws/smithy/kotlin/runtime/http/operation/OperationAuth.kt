/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.AuthSchemeOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeProvider
import aws.smithy.kotlin.runtime.http.auth.AnonymousAuthScheme
import aws.smithy.kotlin.runtime.http.auth.AnonymousIdentityProvider
import aws.smithy.kotlin.runtime.http.auth.HttpAuthScheme
import aws.smithy.kotlin.runtime.identity.IdentityProviderConfig

private val AnonymousAuthConfig = OperationAuthConfig(
    { listOf(AuthSchemeOption(AuthSchemeId.Anonymous)) },
    configuredAuthSchemes = listOf(AnonymousAuthScheme),
    { AnonymousIdentityProvider },
)

/**
 * Container for authentication configuration for an operation
 * @param authSchemeResolver component capable of resolving authentication scheme candidates for the current operation
 * @param configuredAuthSchemes configured auth schemes, used to select from the candidates
 * @param identityProviderConfig configured identity providers
 */
@InternalApi
public data class OperationAuthConfig(
    val authSchemeResolver: AuthSchemeResolver,
    // FIXME - build the map in this class
    val configuredAuthSchemes: List<HttpAuthScheme>,
    val identityProviderConfig: IdentityProviderConfig,
) {
    public companion object {
        public val Anonymous: OperationAuthConfig = AnonymousAuthConfig

        /**
         * Convenience function to create auth configuration from configured auth schemes.
         * This creates an [AuthSchemeResolver] that returns all configured auth scheme ID's.
         */
        public fun from(
            identityProviderConfig: IdentityProviderConfig,
            vararg authSchemes: HttpAuthScheme,
        ): OperationAuthConfig {
            val resolver = AuthSchemeResolver { authSchemes.map { AuthSchemeOption(it.schemeId) } }
            return OperationAuthConfig(resolver, authSchemes.toList(), identityProviderConfig)
        }
    }
}

/**
 * Type agnostic version of [AuthSchemeProvider]. Typically service client specific versions are code generated and
 * then adapted to this generic version for actually executing a request.
 */
@InternalApi
public fun interface AuthSchemeResolver {
    /**
     * Resolve the candidate authentication schemes for an operation
     */
    public suspend fun resolve(request: SdkHttpRequest): List<AuthSchemeOption>
}
