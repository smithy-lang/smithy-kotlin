/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.identity.IdentityProviderConfig
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * A no-op signer that does nothing with the request
 */
public object AnonymousHttpSigner : HttpSigner {
    override suspend fun sign(signingRequest: SignHttpRequest) {}
}

/**
 * Anonymous identity
 */
public object AnonymousIdentity : Identity {
    override val expiration: Instant? = null
    override val attributes: Attributes = emptyAttributes()
}

public object AnonymousIdentityProvider : IdentityProvider {
    override suspend fun resolve(attributes: Attributes): Identity = AnonymousIdentity
}

/**
 * A no-op auth scheme
 */
public object AnonymousAuthScheme : AuthScheme {
    override val schemeId: AuthSchemeId = AuthSchemeId.Anonymous
    override val signer: HttpSigner = AnonymousHttpSigner
    override fun identityProvider(identityProviderConfig: IdentityProviderConfig): IdentityProvider = AnonymousIdentityProvider
}
