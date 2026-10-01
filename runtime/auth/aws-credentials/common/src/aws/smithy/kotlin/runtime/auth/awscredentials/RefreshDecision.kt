/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.time.Instant
import kotlin.time.Duration

/**
 * One cached resolution, with every deadline derived from it precomputed.
 *
 * Keeping the deadlines in the entry rather than in sibling fields makes it impossible for them to describe
 * credentials other than [credentials]. The entry is immutable: every transition below returns a new one.
 *
 * A source that stated no expiration leaves [expiresAt] and [mandatoryAt] null. That is what makes it
 * impossible to block a caller for, or fail a caller against, a deadline this class invented.
 */
internal data class CacheEntry(
    val credentials: Credentials,
    val policy: RefreshPolicy,
    val expiresAt: Instant?, // null when the source stated no expiration
    val advisoryAt: Instant,
    val mandatoryAt: Instant?, // null with [expiresAt]: nothing to block for
    val nextRefreshAllowedAt: Instant? = null,
)

/** How badly a refresh is needed at a given instant. */
internal enum class RefreshUrgency {
    /** The cached credentials are fresh. Serve them. */
    None,

    /** Past the advisory deadline: refresh if convenient, but never block a caller and tolerate failure. */
    Advisory,

    /** Nothing cached, past the mandatory deadline, or invalidated: the caller waits for a refresh. */
    Mandatory,
}

/**
 * The single freshness predicate for the whole lifecycle.
 *
 * Pure and total: it takes the instant rather than a clock, and returns a decision rather than acting on one. The
 * behavior can therefore be pinned by a table of `(entry, now, invalidated)` cases with no coroutines and no fake
 * source involved.
 *
 * Every path that needs this question answered calls here — the resolve path, and the re-check each refresh performs
 * after taking the lock — so the three cannot drift apart. They previously each carried their own copy of the
 * comparison.
 */
internal fun CacheEntry?.urgency(now: Instant, invalidated: Boolean = false): RefreshUrgency = when {
    this == null || invalidated -> RefreshUrgency.Mandatory
    now < advisoryAt -> RefreshUrgency.None
    mandatoryAt == null || now < mandatoryAt -> RefreshUrgency.Advisory // undated: never mandatory
    else -> RefreshUrgency.Mandatory
}

/**
 * Whether the backoff installed by the last failed refresh has elapsed.
 *
 * An entry that has never failed a refresh carries no deadline and is always eligible.
 */
internal fun CacheEntry.mayAttemptRefresh(now: Instant): Boolean = nextRefreshAllowedAt?.let { now >= it } ?: true

/**
 * The credentials this entry may still be used with at [now], or null if it may not be used at all.
 *
 * Static stability is exactly this predicate: a provider in scope keeps signing with what it has even past expiry,
 * while everything else may use the cached value only while it is genuinely still valid. A value whose source
 * stated no expiration is always usable, because there is no expiry for it to be past.
 *
 * Deliberately takes only [now]. An invalidated entry is still usable: invalidation raises urgency, it does not
 * discard the credentials and it does not bypass the refresh backoff. Adding the rejected key here would do both.
 */
internal fun CacheEntry.usableAt(now: Instant): Credentials? = if (policy.staticStability || expiresAt == null || now < expiresAt) credentials else null

/**
 * The same entry with a refresh backoff installed.
 *
 * `copy` carries `expiresAt`, `advisoryAt` and `mandatoryAt` forward untouched, so the refresh windows stay fixed for
 * the lifetime of this credential set: the window must not be recomputed for the same credentials after a failed
 * refresh. Only `nextRefreshAllowedAt` moves. Do not "simplify" this to a fresh [RefreshTiming.entryFor] call — that
 * would recompute the windows from the *remaining* lifetime and shrink them on every failure.
 */
internal fun CacheEntry.withBackoff(now: Instant, backoff: Duration): CacheEntry = copy(nextRefreshAllowedAt = now + backoff)

/**
 * Turns a resolved credential into a [CacheEntry], applying the configured window sizes.
 *
 * Separated from the cache because it is pure: the same inputs always produce the same deadlines, which is exactly
 * what the window tests assert. Validation of the window configuration lives here too, so an invalid combination
 * fails at construction rather than on the first refresh.
 *
 * @param defaultLifetime the lifetime assumed for credentials that carry no expiration
 * @param configuredAdvisoryWindow overrides the computed advisory window. Must be at least [mandatoryRefreshWindow].
 * Intended for testing; this is not exposed to customers.
 * @param mandatoryRefreshWindow how long before expiry a refresh becomes blocking
 */
internal class RefreshTiming(
    private val defaultLifetime: Duration,
    private val configuredAdvisoryWindow: Duration?,
    private val mandatoryRefreshWindow: Duration,
) {
    init {
        require(mandatoryRefreshWindow > Duration.ZERO) {
            "mandatoryRefreshWindow must be positive, got $mandatoryRefreshWindow"
        }
        configuredAdvisoryWindow?.let {
            require(it >= mandatoryRefreshWindow) {
                "configuredAdvisoryWindow ($it) must be >= mandatoryRefreshWindow ($mandatoryRefreshWindow)"
            }
        }
    }

    fun entryFor(credentials: Credentials, policy: RefreshPolicy, now: Instant): CacheEntry {
        // A credential the source did not date does not expire, so no deadline is invented for it: defaultLifetime
        // places the re-resolution point and nothing else. A stated expiration is used as given, and is never
        // shortened to defaultLifetime.
        val expiresAt = credentials.expiration
        val horizon = expiresAt ?: (now + defaultLifetime)

        val advisory = (configuredAdvisoryWindow ?: policy.advisoryWindowFor(horizon - now))
            .coerceAtLeast(mandatoryRefreshWindow)

        // How often a value is re-read when its own expiration is not what decides. A policy may name a tighter
        // cadence - an in-process source costs one lookup to re-read and can change between resolves - but never one
        // slower than the default lifetime, which is also what keeps this positive for a small default lifetime.
        val pace = policy.undatedCadence?.coerceAtMost(defaultLifetime) ?: defaultLifetime

        // A source this feature does not govern keeps that pace whether or not its value is dated: its refresh timing
        // is not ours to change. The expiration still bounds use, through expiresAt and mandatoryAt below; what it
        // stops doing on such a policy is deciding when to look again.
        val advisoryAt = when {
            expiresAt == null -> now + pace
            policy.pacedRefresh -> minOf(expiresAt - advisory, now + pace)
            else -> expiresAt - advisory
        }

        return CacheEntry(
            credentials = credentials,
            policy = policy,
            expiresAt = expiresAt,
            advisoryAt = advisoryAt,
            mandatoryAt = expiresAt?.minus(mandatoryRefreshWindow),
        )
    }
}
