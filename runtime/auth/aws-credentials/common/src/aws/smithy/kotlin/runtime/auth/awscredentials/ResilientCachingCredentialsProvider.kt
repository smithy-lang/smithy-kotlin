/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.toMutableAttributes
import aws.smithy.kotlin.runtime.identity.Identity
import aws.smithy.kotlin.runtime.io.closeIfCloseable
import aws.smithy.kotlin.runtime.telemetry.logging.logger
import aws.smithy.kotlin.runtime.telemetry.logging.trace
import aws.smithy.kotlin.runtime.time.Clock
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Source of jitter for the refresh lifecycle. Injectable so tests can pin the values.
 */
@InternalApi
public interface RefreshJitter {
    /** Time to wait before another refresh is attempted after a failure. Uniform random 300-600s. */
    public fun refreshBackoff(): Duration

    /** How long a non-recoverable error is cached. Uniform random 1-5s. */
    public fun errorCacheTtl(): Duration

    @InternalApi
    public companion object {
        public val Default: RefreshJitter = object : RefreshJitter {
            private val random = Random.Default
            override fun refreshBackoff(): Duration = random.nextInt(300, 601).seconds
            override fun errorCacheTtl(): Duration = random.nextInt(1_000, 5_001).milliseconds
        }
    }
}

/**
 * Marks a [CredentialsProvider] as already implementing the refresh lifecycle, so wrapping it again would stack
 * two independent caches.
 */
@InternalApi
public interface RefreshAwareCredentialsProvider : CredentialsProvider

/**
 * A caching [CredentialsProvider] implementing the standard AWS SDK credential refresh behavior: two-window
 * refresh timing, static stability, rate-limited retry after failure, and immediate propagation of
 * non-recoverable errors.
 *
 * Which of those behaviors applies is decided **per resolved value**, from the [CredentialsRefreshBehavior] the
 * source declared on the credentials (see [policyFor]). One instance therefore wraps an entire provider chain while
 * still honoring per-provider scoping — IMDS credentials get static stability, Process credentials get caching
 * only, and a provider that declares nothing gets the conservative fail-safe.
 *
 * This class holds no policy of its own. Every decision it makes is a call into [urgency], [mayAttemptRefresh] or
 * [usableAt]; what remains here is the lock discipline, the call to the source, the logging, and `close`.
 *
 * @param source the provider to resolve credentials from
 * @param defaultLifetime the lifetime assumed for credentials that carry no expiration
 * @param configuredAdvisoryWindow overrides the computed advisory window. Must be at least
 * [mandatoryRefreshWindow]. Intended for testing; this is not exposed to customers.
 * @param mandatoryRefreshWindow how long before expiry a refresh becomes blocking
 * @param clock the source of time
 * @param jitter the source of jitter for backoff and error caching
 */
@InternalApi
public class ResilientCachingCredentialsProvider(
    private val source: CredentialsProvider,
    defaultLifetime: Duration = DEFAULT_CREDENTIALS_REFRESH_SECONDS.seconds,
    configuredAdvisoryWindow: Duration? = null,
    mandatoryRefreshWindow: Duration = 1.minutes,
    private val clock: Clock = Clock.System,
    private val jitter: RefreshJitter = RefreshJitter.Default,
) : CloseableCredentialsProvider,
    RefreshAwareCredentialsProvider {

    private val timing = RefreshTiming(defaultLifetime, configuredAdvisoryWindow, mandatoryRefreshWindow)
    private val errors = NonRecoverableErrorCache(clock)
    private val invalidation = InvalidationSignal()

    // Serializes refreshes. Advisory refreshes use tryLock (skip if busy); mandatory refreshes wait.
    private val refreshLock = Mutex()

    // Read on every resolve without holding the lock, so it must be atomic. Written only under refreshLock.
    private val state = atomic<CacheEntry?>(null)

    private val closed = atomic(false)

    override suspend fun resolve(attributes: Attributes): Credentials {
        check(!closed.value) { "Credentials provider is closed" }

        val entry = state.value
        // A rejection naming the credentials we hold forces the mandatory path, which is still subject to the refresh
        // backoff. One naming anything else refers to credentials already replaced, so it is discarded.
        val invalidated = entry != null && invalidation.consumeIfMatches(entry.credentials)
        if (entry == null) return refreshBlocking(attributes, invalidated = false)

        return when (entry.urgency(clock.now(), invalidated)) {
            RefreshUrgency.None -> entry.credentials
            RefreshUrgency.Advisory -> refreshAdvisory(attributes, entry)
            RefreshUrgency.Mandatory -> refreshBlocking(attributes, invalidated)
        }
    }

    /**
     * Best-effort refresh inside the advisory window. Never blocks: if another coroutine is already refreshing, the
     * caller gets the cached credentials. A failure here is swallowed unless it is non-recoverable.
     */
    private suspend fun refreshAdvisory(attributes: Attributes, fallback: CacheEntry): Credentials {
        errors.throwIfLive()
        if (!fallback.mayAttemptRefresh(clock.now())) return fallback.credentials
        if (!refreshLock.tryLock()) return fallback.credentials

        return try {
            // Re-check: another coroutine may have refreshed between the read and acquiring the lock.
            val current = state.value ?: fallback
            val now = clock.now()
            if (current.urgency(now) == RefreshUrgency.None) return current.credentials
            if (!current.mayAttemptRefresh(now)) return current.credentials

            try {
                install(fetch(attributes), current).credentials
            } catch (ex: Exception) {
                if (ex.isNonRecoverableCredentialsError()) {
                    errors.record(ex, jitter.errorCacheTtl())
                    throw ex
                }
                onRefreshFailed(current, ex)
                current.credentials
            }
        } finally {
            refreshLock.unlock()
        }
    }

    /**
     * Refresh, waiting for the lock. Used for the initial resolution, inside the mandatory window, and after a
     * rejection. On failure, [usableAt] decides whether the stale credentials are returned or the error is raised.
     *
     * [invalidated] is passed in rather than re-read, because the resolve path already consumed the marker. Re-reading
     * it here would find nothing and fall into the "already fresh enough" shortcut below, which would hand back the
     * very credentials the service rejected.
     */
    private suspend fun refreshBlocking(
        attributes: Attributes,
        invalidated: Boolean,
    ): Credentials = refreshLock.withLock {
        errors.throwIfLive()

        val current = state.value
        // Either the caller consumed a rejection for these credentials, or one arrived while we waited
        // for the lock.
        val rejected = invalidated || (current != null && invalidation.consumeIfMatches(current.credentials))

        if (current != null) {
            val now = clock.now()
            val eligible = current.mayAttemptRefresh(now)
            if (!rejected) {
                when (current.urgency(now)) {
                    // Another coroutine refreshed while we waited for the lock.
                    RefreshUrgency.None -> return@withLock current.credentials
                    // Still inside the advisory window: a live backoff means there is nothing useful to do.
                    RefreshUrgency.Advisory -> if (!eligible) return@withLock current.credentials
                    // Past the mandatory deadline with a live backoff: serve the stale value if policy allows it.
                    RefreshUrgency.Mandatory -> if (!eligible) return@withLock staleOrThrow(current, null)
                }
            } else if (!eligible) {
                // A rejection deliberately does not bypass the backoff. Re-arm so the next resolve tries again.
                invalidation.rearm(current.credentials)
                return@withLock current.credentials
            }
        }

        try {
            install(fetch(attributes), current).credentials
        } catch (ex: Exception) {
            if (ex.isNonRecoverableCredentialsError()) {
                errors.record(ex, jitter.errorCacheTtl())
                throw ex
            }
            if (current == null) throw ex // nothing cached: nothing to be stable against
            onRefreshFailed(current, ex)
            staleOrThrow(current, ex)
        }
    }

    /**
     * The one place this class calls its source.
     *
     * [CallerOwnsCredentialsRefresh] is added to the attributes passed down so that a provider which would otherwise
     * pace or cache on its own resolves straight through instead - this instance is the lifecycle. The copy costs one
     * small allocation per source call, not per request.
     */
    private suspend fun fetch(attributes: Attributes): Credentials = source.resolve(attributes.toMutableAttributes().apply { set(CallerOwnsCredentialsRefresh, true) })

    /**
     * Installs a freshly resolved credential, computing its policy and deadlines.
     *
     * The policy is always computed from the value being installed, never carried over from [previous]. This is the
     * invariant that makes a single cache safe in front of a chain whose composition can change between resolutions:
     * [CacheEntry.policy] describes [CacheEntry.credentials] and nothing else. A `ProfileCredentialsProvider`
     * re-reads the config file on every resolve, so consecutive refreshes through one cache can legitimately
     * produce credentials from different providers with different policies.
     *
     * A source that returns already-expired credentials is treated as a failed refresh, not a success — the
     * credential-expiration-extension case, generalized from IMDS to every static-stability provider.
     */
    private suspend fun install(fetched: Credentials, previous: CacheEntry?): CacheEntry {
        val policy = policyFor(fetched.refreshBehavior)
        val now = clock.now()

        // An already-expired response is a failed refresh for every provider, not just the static-stability ones:
        // the source is reachable but no longer producing fresh credentials. Not gated on policy —
        // what differs by policy is whether the retained credentials may be *used* past expiry (staleOrThrow), not
        // whether an expired response counts as a failure.
        if (fetched.expiration?.let { it <= now } == true) {
            // Fall back to the previous entry, which keeps its own policy — it describes its own credentials.
            val fallback = previous ?: timing.entryFor(fetched, policy, now)
            val extended = onExpiredResponse(fallback)

            // On a cold cache the expired response is itself the only entry, so whether it may be served is the same
            // question staleOrThrow asks of a retained one: a static-stability source keeps signing with what it has,
            // and anything else must not be handed an already-expired credential.
            if (previous == null) staleOrThrow(extended, null)
            return extended
        }

        val entry = timing.entryFor(fetched, policy, now)
        state.value = entry
        errors.clear()
        // Cleared unconditionally, including when the source hands back the same access key ID it just had - a
        // source vending long-term keys will. Keeping the marker in that case would leave the entry permanently
        // mandatory and attempt a refresh on every resolve; clearing it costs one refresh per rejection, which the
        // refresh backoff already bounds.
        invalidation.clear()
        coroutineContext.trace<ResilientCachingCredentialsProvider> {
            "refreshed credentials from ${fetched.providerName ?: "unknown provider"}; " +
                "expiration=${entry.expiresAt ?: "none"}, advisory=${entry.advisoryAt}, " +
                "mandatory=${entry.mandatoryAt ?: "none"}"
        }
        return entry
    }

    /**
     * The source returned credentials that are already expired. Keep using what we have and back off, matching the
     * behavior IMDS implements today for its own credentials.
     */
    private suspend fun onExpiredResponse(entry: CacheEntry): CacheEntry {
        val backoff = jitter.refreshBackoff()
        val updated = entry.withBackoff(clock.now(), backoff)
        state.value = updated
        coroutineContext.logger<ResilientCachingCredentialsProvider>().warn {
            "Attempting credential expiration extension due to a credential service availability issue. " +
                "A refresh of these credentials will be attempted again in ${backoff.inWholeSeconds} seconds."
        }
        return updated
    }

    /**
     * A refresh failed for a recoverable reason: install the backoff and warn.
     *
     * [withBackoff] is what keeps the refresh windows fixed across repeated failures; see its documentation. The
     * message wording is chosen so the text is comparable across AWS SDKs, and `ex` is passed to the logger so the
     * credential source's own error is included.
     */
    private suspend fun onRefreshFailed(entry: CacheEntry, ex: Throwable) {
        val backoff = jitter.refreshBackoff()
        state.value = entry.withBackoff(clock.now(), backoff)
        coroutineContext.logger<ResilientCachingCredentialsProvider>().warn(ex) {
            "Credential refresh failed: ${ex.message}. The SDK will continue using cached credentials. " +
                "A refresh of these credentials will be attempted again after ${backoff.inWholeSeconds} seconds."
        }
    }

    private fun staleOrThrow(entry: CacheEntry, cause: Throwable?): Credentials = entry.usableAt(clock.now()) ?: throw CredentialsProviderException(
        "Credential refresh failed and the cached credentials for " +
            "${entry.credentials.providerName ?: "an unknown provider"} are expired",
        cause,
    )

    /**
     * Records that a target service rejected [rejectedIdentity], so that the next resolution refreshes rather than
     * returning it again.
     *
     * By design this does **not** discard the credentials and does **not** bypass the refresh backoff — it upgrades
     * the next resolution to the mandatory path, which is still gated. No cache entry is rewritten: the deadlines
     * continue to describe the credentials they were computed from, and the [Credentials] object itself is never
     * modified (rewriting its expiration would hand a falsified expiry to the signer).
     *
     * The parameter stays [Identity] because [invalidate] is declared on `IdentityProvider`, whose implementors
     * include token providers that have no access key. Narrowing to [Credentials] is this cache's business, not
     * the interface's.
     */
    override suspend fun invalidate(rejectedIdentity: Identity) {
        if (closed.value) return
        val rejected = rejectedIdentity as? Credentials ?: return
        invalidation.record(rejected)
        coroutineContext.trace<ResilientCachingCredentialsProvider> {
            "credentials were rejected by the target service; a refresh is due on the next resolution"
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        state.value = null
        errors.clear()
        invalidation.clear()
        source.closeIfCloseable()
    }

    override fun toString(): String = "${this.simpleClassName}: ${this.source}"
}

/**
 * Wraps this provider in a [ResilientCachingCredentialsProvider].
 */
@InternalApi
public fun CredentialsProvider.resilientlyCached(
    defaultLifetime: Duration = DEFAULT_CREDENTIALS_REFRESH_SECONDS.seconds,
    configuredAdvisoryWindow: Duration? = null,
    mandatoryRefreshWindow: Duration = 1.minutes,
    clock: Clock = Clock.System,
    jitter: RefreshJitter = RefreshJitter.Default,
): CredentialsProvider = ResilientCachingCredentialsProvider(
    source = this,
    defaultLifetime = defaultLifetime,
    configuredAdvisoryWindow = configuredAdvisoryWindow,
    mandatoryRefreshWindow = mandatoryRefreshWindow,
    clock = clock,
    jitter = jitter,
)

/**
 * Wraps this provider in a [ResilientCachingCredentialsProvider] unless it already implements the refresh
 * lifecycle, so that wrapping a provider twice does not produce two caches with independent state.
 */
@InternalApi
public fun CredentialsProvider.resilientlyCachedIfNeeded(): CredentialsProvider = if (this is RefreshAwareCredentialsProvider) this else resilientlyCached()
