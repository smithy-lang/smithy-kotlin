/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.io.Closeable
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

/**
 * A [TokenProvider] with [Closeable] resources. Users SHOULD call [close] when done with the provider to ensure
 * any held resources are properly released.
 *
 * Implementations SHOULD evict any previously-retrieved or stored credentials when the provider is closed.
 */
public interface CloseableTokenProvider : TokenProvider, Closeable
