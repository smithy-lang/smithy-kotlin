/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The freshness predicates are pure, so they are pinned as a table with no clock, no coroutines and no source.
 * Several of these states were previously reachable only by driving a whole provider through a fake clock.
 */
class RefreshDecisionTest {
    private val epoch = Instant.fromIso8601("2020-10-16T03:56:00Z")

    private fun entry(
        expiresAt: Instant? = epoch + 1.hours,
        advisoryAt: Instant = epoch + 30.minutes,
        mandatoryAt: Instant? = epoch + 59.minutes,
        nextRefreshAllowedAt: Instant? = null,
        policy: RefreshPolicy = RefreshPolicy.FullLifecycle,
        credentials: Credentials = testCredentials(),
    ) = CacheEntry(credentials, policy, expiresAt, advisoryAt, mandatoryAt, nextRefreshAllowedAt)

    @Test
    fun testUrgencyTable() {
        val e = entry()
        assertEquals(RefreshUrgency.Mandatory, null.urgency(epoch), "no entry")
        assertEquals(RefreshUrgency.None, e.urgency(epoch), "well before advisory")
        assertEquals(RefreshUrgency.None, e.urgency(e.advisoryAt - 1.seconds), "just before advisory")
        assertEquals(RefreshUrgency.Advisory, e.urgency(e.advisoryAt), "exactly at advisory")
        assertEquals(RefreshUrgency.Advisory, e.urgency(epoch + 45.minutes), "between the deadlines")
        assertEquals(RefreshUrgency.Mandatory, e.urgency(assertNotNull(e.mandatoryAt)), "exactly at mandatory")
        assertEquals(RefreshUrgency.Mandatory, e.urgency(epoch + 2.hours), "past expiry")
    }

    @Test
    fun testInvalidatedIsMandatoryFromEveryState() {
        val e = entry()
        listOf(epoch, e.advisoryAt, epoch + 45.minutes, epoch + 2.hours).forEach { now ->
            assertEquals(RefreshUrgency.Mandatory, e.urgency(now, invalidated = true), "at $now")
        }
        // including from the freshest state there is, which is the one an "already fresh enough" shortcut would take
        assertEquals(RefreshUrgency.None, e.urgency(epoch, invalidated = false))
    }

    @Test
    fun testUndatedIsNeverMandatory() {
        val e = entry(expiresAt = null, advisoryAt = epoch + 15.minutes, mandatoryAt = null)
        assertEquals(RefreshUrgency.None, e.urgency(epoch))
        assertEquals(RefreshUrgency.Advisory, e.urgency(e.advisoryAt))
        assertEquals(RefreshUrgency.Advisory, e.urgency(epoch + 100.hours), "no deadline was invented for it")
    }

    @Test
    fun testMayAttemptRefresh() {
        assertEquals(true, entry().mayAttemptRefresh(epoch), "no backoff installed")

        val backed = entry(nextRefreshAllowedAt = epoch + 5.minutes)
        assertEquals(false, backed.mayAttemptRefresh(epoch), "before")
        assertEquals(false, backed.mayAttemptRefresh(epoch + 5.minutes - 1.seconds), "just before")
        assertEquals(true, backed.mayAttemptRefresh(epoch + 5.minutes), "exactly at")
        assertEquals(true, backed.mayAttemptRefresh(epoch + 6.minutes), "after")
    }

    @Test
    fun testUsableAt() {
        val expiry = epoch + 1.hours

        // static stability: usable past expiry
        val stable = entry(policy = RefreshPolicy.FullLifecycle)
        assertNotNull(stable.usableAt(epoch))
        assertNotNull(stable.usableAt(expiry + 1.hours))

        // caching only: usable while valid, not after
        val cachingOnly = entry(policy = RefreshPolicy.CachingOnly)
        assertNotNull(cachingOnly.usableAt(epoch))
        assertNull(cachingOnly.usableAt(expiry))
        assertNull(cachingOnly.usableAt(expiry + 1.seconds))

        // undated: always usable, there is no expiry to be past
        val undated = entry(expiresAt = null, mandatoryAt = null, policy = RefreshPolicy.CachingOnly)
        assertNotNull(undated.usableAt(epoch + 100.hours))
    }

    @Test
    fun testUsableAtIgnoresInvalidation() {
        // invalidation raises urgency; it does not make the held credentials unusable, and it does not bypass backoff
        val e = entry(policy = RefreshPolicy.CachingOnly, nextRefreshAllowedAt = epoch + 5.minutes)
        assertSame(e.credentials, e.usableAt(epoch))
    }

    @Test
    fun testWithBackoffMovesOnlyTheBackoff() {
        val e = entry()
        val backed = e.withBackoff(epoch + 30.minutes, 5.minutes)

        assertEquals(e.expiresAt, backed.expiresAt)
        assertEquals(e.advisoryAt, backed.advisoryAt)
        assertEquals(e.mandatoryAt, backed.mandatoryAt)
        assertSame(e.credentials, backed.credentials)
        assertSame(e.policy, backed.policy)
        assertEquals(epoch + 35.minutes, backed.nextRefreshAllowedAt)
    }

    @Test
    fun testWindowsStayFixedAcrossRepeatedFailures() {
        // the window is computed once per credential set and must not be re-derived from the remaining lifetime
        var e = entry()
        repeat(3) { i ->
            e = e.withBackoff(epoch + (30 + i * 6).minutes, 5.minutes)
            assertEquals(epoch + 1.hours, e.expiresAt)
            assertEquals(epoch + 30.minutes, e.advisoryAt)
            assertEquals(epoch + 59.minutes, e.mandatoryAt)
        }
    }

    // --- RefreshTiming ---

    private fun timing(
        defaultLifetime: Duration = 15.minutes,
        configuredAdvisoryWindow: Duration? = null,
        mandatoryRefreshWindow: Duration = 1.minutes,
    ) = RefreshTiming(defaultLifetime, configuredAdvisoryWindow, mandatoryRefreshWindow)

    @Test
    fun testWindowTableAtBothBoundaries() {
        // <= 20m -> 5m; < 90m -> 15m; else 60m. Asserted through entryFor, which is what the cache uses.
        val cases = listOf(
            20.minutes to 5.minutes,
            20.minutes + 1.seconds to 15.minutes,
            89.minutes to 15.minutes,
            90.minutes to 60.minutes,
            2.hours to 60.minutes,
        )

        cases.forEach { (lifetime, window) ->
            val expiry = epoch + lifetime
            val e = timing().entryFor(testCredentials(expiration = expiry), RefreshPolicy.FullLifecycle, epoch)
            assertEquals(expiry - window, e.advisoryAt, "lifetime=$lifetime")
            assertEquals(expiry - 1.minutes, e.mandatoryAt, "lifetime=$lifetime")
            assertEquals(expiry, e.expiresAt, "lifetime=$lifetime")
        }
    }

    @Test
    fun testConfiguredAdvisoryWindowOverrides() {
        val expiry = epoch + 2.hours
        val e = timing(configuredAdvisoryWindow = 10.minutes)
            .entryFor(testCredentials(expiration = expiry), RefreshPolicy.FullLifecycle, epoch)
        assertEquals(expiry - 10.minutes, e.advisoryAt, "the override wins over the 60-minute table row")
    }

    @Test
    fun testInvalidWindowConfigurationFails() {
        assertFailsWith<IllegalArgumentException> {
            timing(configuredAdvisoryWindow = 30.seconds, mandatoryRefreshWindow = 1.minutes)
        }
        assertFailsWith<IllegalArgumentException> {
            timing(mandatoryRefreshWindow = Duration.ZERO)
        }
    }

    @Test
    fun testShortLifetimesDoNotInvert() {
        // a lifetime under the smallest table row refreshes on every resolve rather than producing an inverted window
        val expiry = epoch + 90.seconds
        val e = timing().entryFor(testCredentials(expiration = expiry), RefreshPolicy.FullLifecycle, epoch)
        assertEquals(expiry - 5.minutes, e.advisoryAt)
        assertEquals(expiry - 1.minutes, e.mandatoryAt)
        assertEquals(RefreshUrgency.Advisory, e.urgency(epoch), "already advisory at issue")
        assertEquals(true, e.advisoryAt <= assertNotNull(e.mandatoryAt), "never inverted")
    }

    @Test
    fun testUndatedEntryHasNoDeadlines() {
        val e = timing(defaultLifetime = 15.minutes)
            .entryFor(testCredentials(), RefreshPolicy.CachingOnly, epoch)
        assertNull(e.expiresAt)
        assertNull(e.mandatoryAt)
        assertEquals(epoch + 15.minutes, e.advisoryAt, "the default lifetime places the re-read and nothing else")
    }

    @Test
    fun testPassthroughPacesUndatedReadsAtAMinute() {
        val t = timing(defaultLifetime = 15.minutes)

        assertEquals(
            epoch + 1.minutes,
            t.entryFor(testCredentials(), RefreshPolicy.Passthrough, epoch).advisoryAt,
            "an out-of-scope source is re-read on the tighter cadence",
        )
        assertEquals(
            epoch + 15.minutes,
            t.entryFor(testCredentials(), RefreshPolicy.CachingOnly, epoch).advisoryAt,
            "the fail-safe bucket keeps the default lifetime; its source may be an HTTP call",
        )
        assertEquals(
            epoch + 15.minutes,
            t.entryFor(testCredentials(), RefreshPolicy.FullLifecycle, epoch).advisoryAt,
        )
    }

    @Test
    fun testCadenceNeverPostponesAReRead() {
        // with a default lifetime shorter than the cadence the shorter one wins
        val e = timing(defaultLifetime = 30.seconds)
            .entryFor(testCredentials(), RefreshPolicy.Passthrough, epoch)
        assertEquals(epoch + 30.seconds, e.advisoryAt)
    }

    @Test
    fun testPacedPolicyCapsADatedAdvisoryDeadline() {
        // a stated expiration still bounds *use*, but on a paced policy it does not decide when to look again
        val expiry = epoch + 10.hours
        val e = timing(defaultLifetime = 15.minutes)
            .entryFor(testCredentials(expiration = expiry), RefreshPolicy.Passthrough, epoch)

        assertEquals(epoch + 1.minutes, e.advisoryAt, "capped by the cadence, not 60 minutes before expiry")
        assertEquals(expiry, e.expiresAt)
        assertEquals(expiry - 1.minutes, e.mandatoryAt)
    }

    @Test
    fun testUnpacedPolicyKeepsADatedAdvisoryDeadline() {
        val expiry = epoch + 10.hours
        val e = timing(defaultLifetime = 15.minutes)
            .entryFor(testCredentials(expiration = expiry), RefreshPolicy.FullLifecycle, epoch)
        assertEquals(expiry - 60.minutes, e.advisoryAt, "a governed source's own expiration decides")
    }

    @Test
    fun testStatedExpirationIsNeverShortenedToTheDefaultLifetime() {
        val expiry = epoch + 12.hours
        val e = timing(defaultLifetime = 15.minutes)
            .entryFor(testCredentials(expiration = expiry), RefreshPolicy.FullLifecycle, epoch)
        assertEquals(expiry, e.expiresAt, "the default lifetime is a fallback, not an override")
    }
}
