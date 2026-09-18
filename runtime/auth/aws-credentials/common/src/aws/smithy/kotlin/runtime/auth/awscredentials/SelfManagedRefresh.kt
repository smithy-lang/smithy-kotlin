/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.io.Closeable
import aws.smithy.kotlin.runtime.io.closeIfCloseable
import aws.smithy.kotlin.runtime.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Signals to a provider that something above it is already running the refresh lifecycle, so it should resolve
 * straight from its source instead of caching or pacing on its own.
 *
 * Set by [ResilientCachingCredentialsProvider] on the attributes it hands to its source, and read by any provider
 * that would otherwise manage refresh itself. It is a property of the *call*, not of the provider instance: the same
 * provider may be held directly by a caller and also appear inside a cached chain.
 *
 * Named for the contract rather than for any one provider, because both ends of the check need to read as
 * intentional to whoever next touches them.
 */
@InternalApi
public val CallerOwnsCredentialsRefresh: AttributeKey<Boolean> =
    AttributeKey("aws.smithy.kotlin#CallerOwnsCredentialsRefresh")

/**
 * Held by a [CredentialsProvider] that must implement the refresh lifecycle when it is used directly, but must not
 * when it sits under a cache that already does.
 *
 * The wrap is built lazily, so an instance living under a cache never allocates one: the gate takes the
 * [CallerOwnsCredentialsRefresh] branch on every call and the holder stays empty for the provider's lifetime.
 *
 * A provider adopts this by moving its existing `resolve` body to a private function, passing a reference to it as
 * [source], and delegating `resolve`, `invalidate` and `close` here.
 *
 * @param source resolves credentials with no caching or pacing of its own
 * @param defaultLifetime the lifetime assumed for credentials that carry no expiration
 * @param clock the source of time
 */
@InternalApi
public class SelfManagedRefresh(
    private val source: suspend (Attributes) -> Credentials,
    defaultLifetime: Duration = DEFAULT_CREDENTIALS_REFRESH_SECONDS.seconds,
    clock: Clock = Clock.System,
) : Closeable {

    private val wrapped = lazy {
        // The wrapped source is an adapter over `source`, not `this`. Passing `this` would also terminate, because
        // the wrap sets the attribute before calling its source, but then the termination argument would live in
        // another class - and in the providers that adopt this, another repository.
        object : CredentialsProvider {
            override suspend fun resolve(attributes: Attributes): Credentials = source(attributes)
        }.resilientlyCached(defaultLifetime = defaultLifetime, clock = clock)
    }

    public suspend fun resolve(attributes: Attributes): Credentials = if (attributes.getOrNull(CallerOwnsCredentialsRefresh) == true) {
        source(attributes) // a cache above owns the lifecycle
    } else {
        wrapped.value.resolve(attributes) // held directly: this is the only cache
    }

    /**
     * Forwards a rejection to the wrap, if one was ever built.
     *
     * Guarded on [Lazy.isInitialized] for the same reason [close] is: building the wrap in order to invalidate it
     * would produce an empty cache holding nothing to invalidate. The existing wrap is the only thing that has the
     * rejected value in it.
     */
    public suspend fun invalidate(rejectedIdentity: Identity) {
        if (wrapped.isInitialized()) wrapped.value.invalidate(rejectedIdentity)
    }

    override fun close() {
        if (wrapped.isInitialized()) wrapped.value.closeIfCloseable()
    }
}
