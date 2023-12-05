/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

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
 * @param expireCredentialsAfter the default expiration time period for sourced credentials. For a given set of
 * cached credentials, the refresh time period will be the minimum of this time and any expiration timestamp on
 * the credentials themselves.
 * @param refreshBufferWindow amount of time before the actual credential expiration time when credentials are
 * considered expired. For example, if credentials are expiring in 15 minutes, and the buffer time is 10 seconds,
 * then any requests made after 14 minutes and 50 seconds will load new credentials. Defaults to 10 seconds.
 * @param clock the source of time for this provider
 *
 * @return the newly-constructed credentials provider
 */
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
            val providerCreds = source.resolve()
            val cacheExpiration = listOfNotNull(providerCreds.expiration, clock.now() + expireCredentialsAfter).min()
            val credsExpiration = providerCreds.expiration ?: cacheExpiration
            val creds = providerCreds.copy(expiration = credsExpiration)
            ExpiringValue(creds, cacheExpiration)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        cachedCredentials.close()
        source.closeIfCloseable()
    }
}

/**
 * A utility function which wraps a [CredentialsProvider] in a [CachedCredentialsProvider].
 *
 * @param expireCredentialsAfter the default expiration time period for sourced credentials. For a given set of
 * cached credentials, the refresh time period will be the minimum of this time and any expiration timestamp on
 * the credentials themselves.
 * @param refreshBufferWindow amount of time before the actual credential expiration time when credentials are
 * considered expired. For example, if credentials are expiring in 15 minutes, and the buffer time is 10 seconds,
 * then any requests made after 14 minutes and 50 seconds will load new credentials. Defaults to 10 seconds.
 * @param clock the source of time for this provider
 * @return the newly-constructed credentials provider
 */
public fun CredentialsProvider.cached(
    expireCredentialsAfter: Duration = DEFAULT_CREDENTIALS_REFRESH_SECONDS.seconds,
    refreshBufferWindow: Duration = DEFAULT_CREDENTIALS_REFRESH_BUFFER_SECONDS.seconds,
    clock: Clock = Clock.System,
): CachedCredentialsProvider = CachedCredentialsProvider(this, expireCredentialsAfter, refreshBufferWindow, clock)
