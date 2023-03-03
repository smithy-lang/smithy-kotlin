/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.identity.IdentityProviderConfig

/**
 * A configured authentication scheme for HTTP protocol
 */
public interface HttpAuthScheme {
    /**
     * The unique authentication scheme ID
     */
    public val schemeId: AuthSchemeId

    /**
     * Retrieve the identity provider associated with this authentication scheme. By default, the
     * [identityProviderConfig] is used to retrieve a suitable identity provider for this authentication scheme.
     *
     * @return an [IdentityProvider] compatible with [schemeId] (and by extension the configured [signer]).
     */
    public fun identityProvider(identityProviderConfig: IdentityProviderConfig): IdentityProvider =
        identityProviderConfig.identityProviderForScheme(schemeId)

    /**
     * The signer used to sign HTTP requests for this auth scheme
     */
    public val signer: HttpSigner
}
