/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.CoreSettings
import aws.smithy.kotlin.runtime.retries.delay.ExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import aws.smithy.kotlin.runtime.util.TestPlatformProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NewRetriesFeatureFlagTest {
    private val flagOff = TestPlatformProvider()
    private val flagOn = TestPlatformProvider(props = mapOf("smithy.newRetries2026" to "true"))
    private val flagOnEnv = TestPlatformProvider(env = mapOf("SMITHY_NEW_RETRIES_2026" to "true"))

    @Test
    fun testResolvesFlagOff() {
        assertFalse(CoreSettings.resolveNewRetriesEnabled(flagOff))
    }

    @Test
    fun testResolvesFlagOn() {
        assertTrue(CoreSettings.resolveNewRetriesEnabled(flagOn))
    }

    @Test
    fun testResolvesFlagOnViaEnv() {
        assertTrue(CoreSettings.resolveNewRetriesEnabled(flagOnEnv))
    }

    @Test
    fun testFlagOffUsesLegacyDefaults() {
        val delayer = ExponentialBackoffWithJitter(ExponentialBackoffWithJitter.Config(ExponentialBackoffWithJitter.Config.Builder(flagOff)))
        assertEquals(10.milliseconds, delayer.config.initialDelay)
        assertEquals(1.5, delayer.config.scaleFactor)
        assertEquals(1.seconds, delayer.config.throttlingBaseDelay)
        assertEquals(5.seconds, delayer.config.retryAfterMaxOvershoot)
        val tokenBucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config(StandardRetryTokenBucket.Config.Builder(flagOff)))
        assertEquals(5, tokenBucket.config.retryCost)
        assertEquals(10, tokenBucket.config.timeoutRetryCost)
    }

    @Test
    fun testFlagOnEnvUsesStandardDefaults() {
        val delayer = ExponentialBackoffWithJitter(ExponentialBackoffWithJitter.Config(ExponentialBackoffWithJitter.Config.Builder(flagOnEnv)))
        assertEquals(50.milliseconds, delayer.config.initialDelay)
        assertEquals(2.0, delayer.config.scaleFactor)
        assertEquals(1.seconds, delayer.config.throttlingBaseDelay)
        assertEquals(5.seconds, delayer.config.retryAfterMaxOvershoot)
        val tokenBucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config(StandardRetryTokenBucket.Config.Builder(flagOnEnv)))
        assertEquals(14, tokenBucket.config.retryCost)
        assertEquals(5, tokenBucket.config.timeoutRetryCost)
    }

    @Test
    fun testFlagOnUsesStandardDefaults() {
        val delayer = ExponentialBackoffWithJitter(ExponentialBackoffWithJitter.Config(ExponentialBackoffWithJitter.Config.Builder(flagOn)))
        assertEquals(50.milliseconds, delayer.config.initialDelay)
        assertEquals(2.0, delayer.config.scaleFactor)
        assertEquals(1.seconds, delayer.config.throttlingBaseDelay)
        assertEquals(5.seconds, delayer.config.retryAfterMaxOvershoot)
        val tokenBucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config(StandardRetryTokenBucket.Config.Builder(flagOn)))
        assertEquals(14, tokenBucket.config.retryCost)
        assertEquals(5, tokenBucket.config.timeoutRetryCost)
    }

    @Test
    fun testFlagOffTransientUsesTimeoutRetryCost() = runTest {
        val bucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config(StandardRetryTokenBucket.Config.Builder(flagOff)))
        val token = bucket.acquireToken()
        token.scheduleRetry(RetryErrorType.Transient)
        assertEquals(500 - 10, bucket.capacity)
    }

    @Test
    fun testFlagOnEnvTransientUsesRetryCost() = runTest {
        val bucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config(StandardRetryTokenBucket.Config.Builder(flagOnEnv)))
        val token = bucket.acquireToken()
        token.scheduleRetry(RetryErrorType.Transient)
        assertEquals(500 - 14, bucket.capacity)
    }

    @Test
    fun testFlagOnTransientUsesRetryCost() = runTest {
        val bucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config(StandardRetryTokenBucket.Config.Builder(flagOn)))
        val token = bucket.acquireToken()
        token.scheduleRetry(RetryErrorType.Transient)
        assertEquals(500 - 14, bucket.capacity)
    }
}
