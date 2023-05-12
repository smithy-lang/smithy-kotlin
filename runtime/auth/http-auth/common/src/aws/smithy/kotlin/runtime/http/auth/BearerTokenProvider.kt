/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.util.Attributes

/**
 * Represents a producer/source of Bearer authentication tokens
 */
public interface BearerTokenProvider : IdentityProvider {
    /**
     * Request a [BearerToken] from the provider
     */
    override suspend fun resolve(attributes: Attributes): BearerToken
}

/**
 * Represents a Bearer token identity
 */
public interface BearerToken : Identity {
    /**
     * The token value.
     *
     * NOTE: tokens are considered opaque values by signers, any encoding (e.g. base64 for bearer tokens) needs
     * to be encoded in the value.
     */
    public val token: String
}

/**
 * A [BearerTokenProvider] with [Closeable] resources. Users SHOULD call [close] when done with the provider to ensure
 * any held resources are properly released.
 *
 * Implementations MUST evict any previously-retrieved or stored credentials when the provider is closed.
 */
public interface CloseableBearerTokenProvider : BearerTokenProvider, Closeable
