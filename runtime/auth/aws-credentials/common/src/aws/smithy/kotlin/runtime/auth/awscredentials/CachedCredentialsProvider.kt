/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.PlannedRemoval
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.io.closeIfCloseable
import aws.smithy.kotlin.runtime.telemetry.logging.trace
import aws.smithy.kotlin.runtime.time.Clock
import aws.smithy.kotlin.runtime.util.CachedValue
import aws.smithy.kotlin.runtime.util.ExpiringValue
import kotlinx.atomicfu.atomic
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val DEFAULT_CREDENTIALS_REFRESH_BUFFER_SECONDS = 10

/**
 * The amount of time credentials are valid for before being refreshed when an explicit value
 * is not given to/from a provider
 */
public const val DEFAULT_CREDENTIALS_REFRESH_SECONDS: Int = 60 * 15

/**
 * **⚠️Important**: This provider is now _deprecated_ and should no longer be necessary. Credentials providers cache
 * and refresh their own credentials, and do so with behavior this provider does not implement: a refresh ahead of
 * expiration, continued use of the cached credentials for a bounded window when a refresh fails, a rate limit on
 * refresh attempts after a failure, and immediate propagation of a non-recoverable error. Remove the wrapper and use
 * the provider directly.
 *
 * Creates a provider that functions as a caching decorator of another provider.
 *
 * Credentials sourced through this provider will be cached within it until their expiration time.
 * When the cached credentials expire, new credentials will be fetched when next queried.
 *
 * For example, the default chain is implemented as:
 *
 * CachedProvider -> ProviderChain(EnvironmentProvider -> ProfileProvider -> ECS/EC2IMD etc...)
 *
 * @param source the provider to cache credentials results from
 * @param expireCredentialsAfter how long to cache credentials that carry no expiration of their own. A credential
 * that states an expiration is cached until that expiration and this value does not apply to it.
 * @param refreshBufferWindow amount of time before the actual credential expiration time when credentials are
 * considered expired. For example, if credentials are expiring in 15 minutes, and the buffer time is 10 seconds,
 * then any requests made after 14 minutes and 50 seconds will load new credentials. Defaults to 10 seconds.
 * @param clock the source of time for this provider
 *
 * @return the newly-constructed credentials provider
 */
@Deprecated(
    "Credentials providers cache and refresh their own credentials, so wrapping one in this provider is no longer " +
        "necessary. Remove the wrapper and use the provider directly.",
)
@PlannedRemoval(major = 1, minor = 9)
@Suppress("DEPRECATION")
public class CachedCredentialsProvider(
    private val source: CredentialsProvider,
    private val expireCredentialsAfter: Duration = DEFAULT_CREDENTIALS_REFRESH_SECONDS.seconds,
    refreshBufferWindow: Duration = DEFAULT_CREDENTIALS_REFRESH_BUFFER_SECONDS.seconds,
    private val clock: Clock = Clock.System,
) : CloseableCredentialsProvider {

    private val cachedCredentials = CachedValue<Credentials>(null, bufferTime = refreshBufferWindow, clock)
    private val closed = atomic(false)

    override suspend fun resolve(attributes: Attributes): Credentials {
        check(!closed.value) { "Credentials provider is closed" }

        return cachedCredentials.getOrLoad {
            coroutineContext.trace<CachedCredentialsProvider> { "refreshing credentials cache" }
            val providerCreds = source.resolve(attributes)
            // An expiration stated by the source is honored in full. [expireCredentialsAfter] places the cache
            // expiration only for a credential the source did not date; it used to also truncate a longer stated
            // one, so a credential valid for 30 minutes was re-resolved after 15.
            val cacheExpiration = providerCreds.expiration ?: (clock.now() + expireCredentialsAfter)
            val creds = providerCreds.copy(expiration = cacheExpiration)
            ExpiringValue(creds, cacheExpiration)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cachedCredentials.close()
        source.closeIfCloseable()
    }

    override fun toString(): String = this.simpleClassName + ": " + this.source.simpleClassName
}

/**
 * **⚠️Important**: This function is now _deprecated_ and should no longer be necessary. Credentials providers cache
 * and refresh their own credentials, and do so with behavior this function does not add: a refresh ahead of
 * expiration, continued use of the cached credentials for a bounded window when a refresh fails, a rate limit on
 * refresh attempts after a failure, and immediate propagation of a non-recoverable error. Remove the call and use the
 * provider directly.
 *
 * A utility function which wraps a [CredentialsProvider] in a [CachedCredentialsProvider].
 *
 * @param expireCredentialsAfter how long to cache credentials that carry no expiration of their own. A credential
 * that states an expiration is cached until that expiration and this value does not apply to it.
 * @param refreshBufferWindow amount of time before the actual credential expiration time when credentials are
 * considered expired. For example, if credentials are expiring in 15 minutes, and the buffer time is 10 seconds,
 * then any requests made after 14 minutes and 50 seconds will load new credentials. Defaults to 10 seconds.
 * @param clock the source of time for this provider
 * @return the newly-constructed credentials provider
 */
@Deprecated(
    "Credentials providers cache and refresh their own credentials, so calling this is no longer necessary. Remove " +
        "the call and use the provider directly.",
)
@PlannedRemoval(major = 1, minor = 9)
@Suppress("DEPRECATION")
public fun CredentialsProvider.cached(
    expireCredentialsAfter: Duration = DEFAULT_CREDENTIALS_REFRESH_SECONDS.seconds,
    refreshBufferWindow: Duration = DEFAULT_CREDENTIALS_REFRESH_BUFFER_SECONDS.seconds,
    clock: Clock = Clock.System,
): CachedCredentialsProvider = CachedCredentialsProvider(this, expireCredentialsAfter, refreshBufferWindow, clock)
