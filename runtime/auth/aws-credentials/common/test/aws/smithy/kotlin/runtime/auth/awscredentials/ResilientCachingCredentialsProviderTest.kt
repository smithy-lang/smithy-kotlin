/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.ErrorMetadata
import aws.smithy.kotlin.runtime.collections.Attributes
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.telemetry.TelemetryProviderContext
import aws.smithy.kotlin.runtime.telemetry.logging.LogLevel
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.time.ManualClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ResilientCachingCredentialsProviderTest {
    private val epoch = Instant.fromIso8601("2020-10-16T03:56:00Z")

    private val stable = CredentialsRefreshBehavior.RefreshableWithStaticStability

    private fun cache(
        source: CredentialsProvider,
        clock: ManualClock,
        defaultLifetime: Duration = 15.minutes,
        configuredAdvisoryWindow: Duration? = null,
        mandatoryRefreshWindow: Duration = 1.minutes,
        jitter: RefreshJitter = TestJitter(),
    ) = ResilientCachingCredentialsProvider(
        source = source,
        defaultLifetime = defaultLifetime,
        configuredAdvisoryWindow = configuredAdvisoryWindow,
        mandatoryRefreshWindow = mandatoryRefreshWindow,
        clock = clock,
        jitter = jitter,
    )

    // --- cached credential behavior ---

    @Test
    fun testFirstResolveCallsTheSource() = runTest {
        val clock = ManualClock(epoch)
        val creds = testCredentials(expiration = epoch + 2.hours, behavior = stable)
        val source = TestCredentialsProvider(listOf(Result.success(creds)))

        assertSame(creds, cache(source, clock).resolve())
        assertEquals(1, source.callCount)
    }

    @Test
    fun testCachedWithinTheAdvisoryWindow() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials(expiration = epoch + 2.hours, behavior = stable))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(30.minutes) // 2h lifetime -> 60m advisory window, so advisory is at +60m
        provider.resolve()
        provider.resolve()

        assertEquals(1, source.callCount)
    }

    @Test
    fun testRefreshedAtTheAdvisoryDeadline() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
                Result.success(testCredentials("AKID2", epoch + 4.hours, stable)),
            ),
        )
        val provider = cache(source, clock)

        assertEquals("AKID1", provider.resolve().accessKeyId)
        clock.advance(1.hours) // exactly at advisoryAt
        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(2, source.callCount)
    }

    @Test
    fun testUndatedCredentialsAreReReadAtTheDefaultLifetime() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1")), Result.success(testCredentials("AKID2"))),
        )
        val provider = cache(source, clock, defaultLifetime = 15.minutes)

        assertEquals("AKID1", provider.resolve().accessKeyId)
        clock.advance(14.minutes)
        assertEquals("AKID1", provider.resolve().accessKeyId)
        clock.advance(1.minutes)
        assertEquals("AKID2", provider.resolve().accessKeyId)
    }

    @Test
    fun testUndatedCredentialsAreNeverFailedAgainstADeadline() = runTest {
        val clock = ManualClock(epoch)
        val cached = testCredentials("AKID1", behavior = stable)
        val source = TestCredentialsProvider(
            listOf(Result.success(cached)) + List(20) { Result.failure(ClientException("network down")) },
        )
        val provider = cache(source, clock)

        provider.resolve()
        // far past now + defaultLifetime, every refresh failing: the value is still served
        repeat(5) {
            clock.advance(1.hours)
            assertSame(cached, provider.resolve(), "an undated value has no deadline to fail against")
        }
    }

    @Test
    fun testUndatedCredentialsAreReplacedOnceARefreshSucceeds() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1")),
                Result.failure(ClientException("network down")),
                Result.success(testCredentials("AKID2")),
            ),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(15.minutes)
        assertEquals("AKID1", provider.resolve().accessKeyId, "failed refresh keeps the value")
        clock.advance(5.minutes) // the 5m backoff lapses
        assertEquals("AKID2", provider.resolve().accessKeyId)
    }

    // --- static stability ---

    @Test
    fun testStaticStabilityServesExpiredCredentials() = runTest {
        val clock = ManualClock(epoch)
        val cached = testCredentials("AKID1", epoch + 30.minutes, stable)
        val source = TestCredentialsProvider(
            listOf(Result.success(cached), Result.failure(ClientException("IMDS unavailable"))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours) // past expiry
        assertSame(cached, provider.resolve(), "in scope: keep signing with what we have")
    }

    @Test
    fun testCachingOnlyThrowsOnceExpired() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 30.minutes)), // declares nothing -> CachingOnly
                Result.failure(ClientException("source unavailable")),
            ),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours)
        val ex = assertFailsWith<CredentialsProviderException> { provider.resolve() }
        assertTrue(ex.message!!.contains("expired"))
    }

    @Test
    fun testCachingOnlyServesUnexpiredCredentialsAfterAFailedRefresh() = runTest {
        val clock = ManualClock(epoch)
        val cached = testCredentials("AKID1", epoch + 2.hours)
        val source = TestCredentialsProvider(
            listOf(Result.success(cached), Result.failure(ClientException("source unavailable"))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours) // advisory, still valid
        assertSame(cached, provider.resolve(), "plain caching still applies")
    }

    @Test
    fun testStaticStabilityDiffersWithinOneCacheInstance() = runTest {
        // the design's central claim: same cache, same call, different outcome, decided by the declaration
        val clock = ManualClock(epoch)

        val stableSource = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 30.minutes, stable)),
                Result.failure(ClientException("unavailable")),
            ),
        )
        val plainSource = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 30.minutes)),
                Result.failure(ClientException("unavailable")),
            ),
        )

        val stableCache = cache(stableSource, clock)
        val plainCache = cache(plainSource, clock)
        stableCache.resolve()
        plainCache.resolve()

        clock.advance(1.hours)
        assertEquals("AKID1", stableCache.resolve().accessKeyId)
        assertFailsWith<CredentialsProviderException> { plainCache.resolve() }
    }

    @Test
    fun testAlreadyExpiredResponseIsTreatedAsAFailedRefresh() = runTest {
        val clock = ManualClock(epoch)
        val cached = testCredentials("AKID1", epoch + 30.minutes, stable)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(cached),
                Result.success(testCredentials("AKID2", epoch + 20.minutes, stable)), // already in the past by then
                Result.success(testCredentials("AKID3", epoch + 10.hours, stable)),
            ),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(30.minutes)
        assertSame(cached, provider.resolve(), "the expired response is rejected and the previous entry retained")
        assertEquals(2, source.callCount)

        // and the backoff was installed, so the third scripted value is not fetched until it lapses
        assertSame(cached, provider.resolve())
        assertEquals(2, source.callCount)
        clock.advance(5.minutes)
        assertEquals("AKID3", provider.resolve().accessKeyId)
    }

    @Test
    fun testExpiredResponseOnAColdCacheFollowsThePolicy() = runTest {
        // Nothing cached, so the expired response is the only entry there is and the policy alone decides. This is
        // the case no existing test covered: standalone IMDS returns such a value today, and through the chain the
        // same response now meets the expiration-extension path.
        val clock = ManualClock(epoch)
        val expired = testCredentials("AKID1", epoch - 1.minutes, stable)

        val stableSource = TestCredentialsProvider(listOf(Result.success(expired)))
        assertSame(
            expired,
            cache(stableSource, clock).resolve(),
            "a static-stability source keeps signing with what it has, as it does standalone today",
        )

        val plainSource = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1", epoch - 1.minutes))),
        )
        assertFailsWith<CredentialsProviderException> { cache(plainSource, clock).resolve() }
    }

    // --- backoff ---

    @Test
    fun testBackoffSuppressesSourceCallsThenLapses() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
                Result.failure(ClientException("unavailable")),
                Result.success(testCredentials("AKID2", epoch + 4.hours, stable)),
            ),
        )
        val provider = cache(source, clock, jitter = TestJitter(backoff = 5.minutes))

        provider.resolve()
        clock.advance(1.hours) // advisory
        provider.resolve()
        assertEquals(2, source.callCount, "the failed attempt")

        clock.advance(1.minutes)
        provider.resolve()
        provider.resolve()
        assertEquals(2, source.callCount, "no attempt while rate limited")

        clock.advance(4.minutes)
        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(3, source.callCount, "exactly one attempt once it lapses")
    }

    @Test
    fun testSuccessfulRefreshClearsTheBackoff() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
                Result.failure(ClientException("unavailable")),
                Result.success(testCredentials("AKID2", epoch + 2.hours, stable)),
                Result.success(testCredentials("AKID3", epoch + 4.hours, stable)),
            ),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours)
        provider.resolve() // fails, backoff installed
        clock.advance(5.minutes)
        assertEquals("AKID2", provider.resolve().accessKeyId) // succeeds; windows recomputed from the new value

        // the new value's own advisory deadline decides now, not a carried-over backoff
        clock.advance(1.minutes)
        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(3, source.callCount)
    }

    // --- non-recoverable errors ---

    private fun nonRecoverable(message: String) = ClientException(message).apply { sdkErrorMetadata.attributes[ErrorMetadata.NonRecoverable] = true }

    @Test
    fun testNonRecoverableErrorIsRaisedRatherThanServedStale() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
                Result.failure(nonRecoverable("SSO session has expired")),
            ),
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours)
        val ex = assertFailsWith<ClientException> { provider.resolve() }
        assertEquals("SSO session has expired", ex.message, "static stability does not apply to a misconfiguration")
    }

    @Test
    fun testNonRecoverableErrorIsCachedThenReAttempted() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.failure(nonRecoverable("role has been revoked")),
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
            ),
        )
        val provider = cache(source, clock, jitter = TestJitter(errorTtl = 2_000.milliseconds))

        assertFailsWith<ClientException> { provider.resolve() }
        assertFailsWith<ClientException> { provider.resolve() }
        assertEquals(1, source.callCount, "a burst of callers makes one doomed call, not N")

        clock.advance(2.seconds)
        assertEquals("AKID1", provider.resolve().accessKeyId)
        assertEquals(2, source.callCount)
    }

    @Test
    fun testNonRecoverableErrorIsFoundThroughAChainWrapping() = runTest {
        val clock = ManualClock(epoch)
        val chainFailure = CredentialsProviderException("no credentials in the chain").apply {
            addSuppressed(nonRecoverable("sso: session expired"))
        }
        val source = TestCredentialsProvider(listOf(Result.failure(chainFailure)))

        assertFailsWith<CredentialsProviderException> { cache(source, clock).resolve() }
    }

    // --- invalidation ---

    @Test
    fun testInvalidationForcesTheNextResolveOntoTheMandatoryPath() = runTest {
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val source = TestCredentialsProvider(
            listOf(Result.success(first), Result.success(testCredentials("AKID2", epoch + 2.hours, stable))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        provider.invalidate(first)
        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(2, source.callCount)
    }

    @Test
    fun testInvalidationInsideTheAdvisoryWindowDoesNotReturnTheRejectedValue() = runTest {
        // the case that pins the "already fresh enough" shortcut defect: an implementation that re-reads the marker
        // inside the blocking refresh finds it consumed, shortcuts, and hands back the rejected credentials forever
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val source = TestCredentialsProvider(
            listOf(Result.success(first), Result.success(testCredentials("AKID2", epoch + 2.hours, stable))),
        )
        val provider = cache(source, clock)

        provider.resolve() // advisory deadline is 60 minutes out
        clock.advance(1.minutes)
        provider.invalidate(first)

        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(2, source.callCount, "exactly one refresh")
    }

    @Test
    fun testInvalidationOfAValueWeNoLongerHoldIsANoOp() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1", epoch + 2.hours, stable))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        provider.invalidate(testCredentials("SOMETHING-ELSE", epoch + 2.hours, stable))
        assertEquals("AKID1", provider.resolve().accessKeyId)
        assertEquals(1, source.callCount)
    }

    @Test
    fun testInvalidationOfANonCredentialsIdentityIsANoOp() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1", epoch + 2.hours, stable))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        provider.invalidate(object : aws.smithy.kotlin.runtime.identity.Identity {
            override val expiration: Instant? = null
            override val attributes: Attributes = emptyAttributes()
        })
        assertEquals(1, source.callCount)
    }

    @Test
    fun testInvalidationDoesNotBypassTheBackoff() = runTest {
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(first),
                Result.failure(ClientException("unavailable")),
                Result.success(testCredentials("AKID2", epoch + 4.hours, stable)),
            ),
        )
        val provider = cache(source, clock, jitter = TestJitter(backoff = 5.minutes))

        provider.resolve()
        clock.advance(1.hours)
        provider.resolve() // fails, backoff installed
        assertEquals(2, source.callCount)

        provider.invalidate(first)
        assertSame(first, provider.resolve(), "a rejection does not license an immediate retry")
        assertEquals(2, source.callCount)

        // but the marker was re-armed, so the refresh happens once the backoff lapses
        clock.advance(5.minutes)
        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(3, source.callCount)
    }

    @Test
    fun testInvalidationRewritesNothing() = runTest {
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val source = TestCredentialsProvider(listOf(Result.success(first)))
        val provider = cache(source, clock)

        val resolved = provider.resolve()
        provider.invalidate(first)

        assertSame(first, resolved, "the Credentials object is never modified")
        assertEquals(epoch + 2.hours, resolved.expiration, "no falsified expiry is handed to the signer")
    }

    @Test
    fun testInvalidationDuringAnInFlightRefreshIsNotDropped() = runTest {
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val gate = CompletableDeferred<Unit>()
        val source = TestCredentialsProvider(
            listOf(
                Result.success(first),
                Result.success(testCredentials("AKID2", epoch + 2.hours, stable)),
                Result.success(testCredentials("AKID3", epoch + 2.hours, stable)),
            ),
            onResolve = { call -> if (call == 1) gate.await() },
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours) // advisory

        coroutineScope {
            val refresh = async { provider.resolve() }
            yield()
            // the rejection names the credentials still installed, and arrives while the refresh is in flight
            provider.invalidate(first)
            gate.complete(Unit)
            refresh.await()
        }

        // AKID2 is installed and clears the marker only if it is the value the rejection named; it is not, so the
        // next resolve must still refresh rather than serve a value the service already rejected
        assertEquals("AKID2", provider.resolve().accessKeyId)
        assertEquals(2, source.callCount)
    }

    // --- concurrency ---

    @Test
    fun testAdvisoryRefreshIsNotBlockingForOtherCallers() = runTest {
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val gate = CompletableDeferred<Unit>()
        val source = TestCredentialsProvider(
            listOf(Result.success(first), Result.success(testCredentials("AKID2", epoch + 4.hours, stable))),
            onResolve = { call -> if (call == 1) gate.await() },
        )
        val provider = cache(source, clock)

        provider.resolve()
        clock.advance(1.hours)

        coroutineScope {
            val refresher = async { provider.resolve() }
            yield()
            // while the refresh is parked, everyone else gets the cached value instead of waiting
            val others = List(4) { async { provider.resolve() } }
            assertTrue(others.awaitAll().all { it === first })
            gate.complete(Unit)
            assertEquals("AKID2", refresher.await().accessKeyId)
        }

        assertEquals(2, source.callCount, "one source call, not five")
    }

    @Test
    fun testMandatoryRefreshCollapsesConcurrentCallersOntoOneSourceCall() = runTest {
        val clock = ManualClock(epoch)
        val gate = CompletableDeferred<Unit>()
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
                Result.success(testCredentials("AKID2", epoch + 4.hours, stable)),
            ),
            onResolve = { call -> if (call == 0) gate.await() },
        )
        val provider = cache(source, clock)

        val resolved = coroutineScope {
            val calls = List(5) { async { provider.resolve() } }
            yield()
            gate.complete(Unit)
            calls.awaitAll()
        }

        assertEquals(1, source.callCount)
        assertTrue(resolved.all { it.accessKeyId == "AKID1" }, "all N get the same refreshed value")
    }

    // --- close ---

    @Test
    fun testCloseClosesTheSourceAndRejectsFurtherResolution() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1", epoch + 2.hours, stable))),
        )
        val provider = cache(source, clock)

        provider.resolve()
        provider.close()

        assertEquals(1, source.closeCount)
        assertFailsWith<IllegalStateException> { provider.resolve() }
    }

    @Test
    fun testCloseIsIdempotentAndSilencesInvalidation() = runTest {
        val clock = ManualClock(epoch)
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val source = TestCredentialsProvider(listOf(Result.success(first)))
        val provider = cache(source, clock)

        provider.resolve()
        provider.close()
        provider.close()
        provider.invalidate(first) // must not throw

        assertEquals(1, source.closeCount)
    }

    @Test
    fun testCloseToleratesANonCloseableSource() = runTest {
        val clock = ManualClock(epoch)
        cache(PlainCredentialsProvider(), clock).close()
    }

    // --- the gate attribute ---

    @Test
    fun testTheSourceIsToldTheCallerOwnsRefresh() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1", epoch + 2.hours, stable))),
        )
        cache(source, clock).resolve()

        assertEquals(true, source.lastAttributes?.getOrNull(CallerOwnsCredentialsRefresh))
    }

    @Test
    fun testTheCallersOwnAttributesAreForwarded() = runTest {
        val clock = ManualClock(epoch)
        val key = aws.smithy.kotlin.runtime.collections.AttributeKey<String>("test#Marker")
        val source = TestCredentialsProvider(
            listOf(Result.success(testCredentials("AKID1", epoch + 2.hours, stable))),
        )
        cache(source, clock).resolve(attributesOf { key to "kept" })

        assertEquals("kept", source.lastAttributes?.getOrNull(key))
    }

    // --- wrapping ---

    @Test
    fun testResilientlyCachedIfNeededDoesNotDoubleWrap() {
        val clock = ManualClock(epoch)
        val alreadyCached = cache(PlainCredentialsProvider(), clock)
        assertSame(alreadyCached, alreadyCached.resilientlyCachedIfNeeded())

        val plain = PlainCredentialsProvider()
        val wrapped = plain.resilientlyCachedIfNeeded()
        assertTrue(wrapped is ResilientCachingCredentialsProvider)
        assertSame(wrapped, wrapped.resilientlyCachedIfNeeded())
    }

    @Test
    fun testToStringNamesTheSource() {
        val clock = ManualClock(epoch)
        val rendered = cache(PlainCredentialsProvider(), clock).toString()
        assertTrue(rendered.contains("ResilientCachingCredentialsProvider"))
        assertTrue(rendered.contains("PlainCredentialsProvider"))
    }

    // --- diagnostics ---

    @Test
    fun testFailedRefreshWarningCarriesTheSourceErrorAndTheRetryDelay() = runTest {
        val clock = ManualClock(epoch)
        val cause = ClientException("IMDS connection reset")
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 2.hours, stable)),
                Result.failure(cause),
            ),
        )
        val logs = RecordingLoggerProvider()
        val provider = cache(source, clock, jitter = TestJitter(backoff = 300.seconds))

        withContext(TelemetryProviderContext(RecordingTelemetryProvider(logs))) {
            provider.resolve()
            clock.advance(1.hours)
            provider.resolve()
        }

        val warning = logs.recordsAt(LogLevel.Warning).single()
        assertTrue(warning.message.contains("IMDS connection reset"), warning.message)
        assertTrue(warning.message.contains("300 seconds"), warning.message)
        assertSame(cause, warning.cause, "warn(ex) {} that drops ex still compiles and still logs")
    }

    @Test
    fun testExpirationExtensionIsWarnedAbout() = runTest {
        val clock = ManualClock(epoch)
        val source = TestCredentialsProvider(
            listOf(
                Result.success(testCredentials("AKID1", epoch + 30.minutes, stable)),
                Result.success(testCredentials("AKID2", epoch + 20.minutes, stable)),
            ),
        )
        val logs = RecordingLoggerProvider()
        val provider = cache(source, clock)

        withContext(TelemetryProviderContext(RecordingTelemetryProvider(logs))) {
            provider.resolve()
            clock.advance(30.minutes)
            provider.resolve()
        }

        val warning = logs.recordsAt(LogLevel.Warning).single()
        assertTrue(warning.message.contains("credential expiration extension"), warning.message)
    }

    // --- SelfManagedRefresh ---
    // Nothing constructs it yet, so its two branches are covered directly rather than through a provider.

    @Test
    fun testSelfManagedRefreshCachesWhenHeldDirectly() = runTest {
        val clock = ManualClock(epoch)
        var calls = 0
        val refresh = SelfManagedRefresh(
            source = {
                calls++
                testCredentials("AKID1", epoch + 2.hours, stable)
            },
            clock = clock,
        )

        refresh.resolve(emptyAttributes())
        clock.advance(30.minutes)
        refresh.resolve(emptyAttributes())

        assertEquals(1, calls, "held directly, the wrap is the only cache and it is doing its job")
        refresh.close()
    }

    @Test
    fun testSelfManagedRefreshDoesNotCacheWhenTheCallerOwnsRefresh() = runTest {
        val clock = ManualClock(epoch)
        var calls = 0
        val refresh = SelfManagedRefresh(
            source = {
                calls++
                testCredentials("AKID1", epoch + 2.hours, stable)
            },
            clock = clock,
        )
        val callerOwns = attributesOf { CallerOwnsCredentialsRefresh to true }

        refresh.resolve(callerOwns)
        refresh.resolve(callerOwns)

        assertEquals(2, calls, "a cache above owns the lifecycle; this must not add a second one")

        // and nothing was built, so close and invalidate are no-ops rather than construction triggers
        refresh.close()
        refresh.invalidate(testCredentials("AKID1"))
        assertEquals(2, calls)
    }

    @Test
    fun testSelfManagedRefreshForwardsInvalidationToTheWrapItBuilt() = runTest {
        val clock = ManualClock(epoch)
        var calls = 0
        val first = testCredentials("AKID1", epoch + 2.hours, stable)
        val second = testCredentials("AKID2", epoch + 2.hours, stable)
        val refresh = SelfManagedRefresh(
            source = { if (calls++ == 0) first else second },
            clock = clock,
        )

        assertEquals("AKID1", refresh.resolve(emptyAttributes()).accessKeyId)
        refresh.invalidate(first)
        assertEquals("AKID2", refresh.resolve(emptyAttributes()).accessKeyId)
        assertEquals(2, calls)
    }

    @Test
    fun testSelfManagedRefreshPassesTheGateDownToItsOwnSource() = runTest {
        // the wrap sets the attribute on what it calls, which is what makes passing a provider its own source safe
        val clock = ManualClock(epoch)
        var sawGate = false
        val refresh = SelfManagedRefresh(
            source = { attributes ->
                sawGate = attributes.getOrNull(CallerOwnsCredentialsRefresh) == true
                testCredentials("AKID1", epoch + 2.hours, stable)
            },
            clock = clock,
        )

        refresh.resolve(emptyAttributes())
        assertTrue(sawGate)
        assertFalse(emptyAttributes().getOrNull(CallerOwnsCredentialsRefresh) == true)
    }
}
