/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.io.Closeable

/**
 * Resolves identities for a service client
 */
public interface IdentityProvider {
    /**
     * Resolve the identity to authenticate requests with
     * @param attributes Additional attributes to feed into identity resolution. Typically, metadata from
     * selecting the authentication scheme.
     * @return An [Identity] that can be used to connect to the service
     */
    public suspend fun resolve(attributes: Attributes = emptyAttributes()): Identity

    /**
     * Signals that [rejectedIdentity] was rejected by a target service with an authentication error, and that any
     * cached copy of it should be refreshed before it is used again.
     *
     * Implementations that cache MUST ignore the call if the identity they hold is not [rejectedIdentity] — a
     * concurrent refresh may already have replaced it. Implementations MUST NOT discard the cached identity and
     * MUST NOT bypass any refresh rate limiting; the identity is marked for refresh, not deleted.
     *
     * The default implementation does nothing, which is correct for any provider that does not cache.
     */
    public suspend fun invalidate(rejectedIdentity: Identity) {}
}

/**
 * A [IdentityProvider] with [Closeable] resources. Users SHOULD call [close] when done with the provider to ensure
 * any held resources are properly released.
 *
 * Implementations MUST evict any previously-retrieved or stored identity when the provider is closed.
 */
public interface CloseableIdentityProvider :
    IdentityProvider,
    Closeable
