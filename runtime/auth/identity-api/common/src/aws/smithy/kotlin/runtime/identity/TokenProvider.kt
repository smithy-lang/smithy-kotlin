/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.util.Attributes

/**
 * Represents a producer/source of authentication tokens (e.g. Bearer auth tokens)
 */
public interface TokenProvider : IdentityProvider {
    /**
     * Request a [Token] from the provider
     */
    override suspend fun resolve(attributes: Attributes): Token
}

/**
 * Represents a token identity (e.g. Bearer token)
 */
public interface Token : Identity {
    /**
     * The token value.
     *
     * NOTE: tokens are considered opaque values by signers, any encoding (e.g. base64 for bearer tokens) needs
     * to be encoded in the value.
     */
    public val token: String
}
