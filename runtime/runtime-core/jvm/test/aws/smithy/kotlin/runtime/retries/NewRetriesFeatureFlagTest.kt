/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.delay.ExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import aws.smithy.kotlin.runtime.retries.policy.RetryErrorType
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NewRetriesFeatureFlagTest {
    private val sysPropKey = "smithy.newRetries2026"

    @BeforeTest
    fun setup() {
        System.clearProperty(sysPropKey)
    }

    @AfterTest
    fun cleanup() {
        System.clearProperty(sysPropKey)
    }

    @Test
    fun testFlagOffUsesLegacyDefaults() {
        val strategy = StandardRetryStrategy()
        val delayer = assertIs<ExponentialBackoffWithJitter>(strategy.config.delayProvider)
        assertEquals(10.milliseconds, delayer.config.initialDelay)
        assertEquals(1.5, delayer.config.scaleFactor)
        assertEquals(1.seconds, delayer.config.throttlingBaseDelay)
        assertEquals(5.seconds, delayer.config.retryAfterMaxOvershoot)
        val tokenBucket = assertIs<StandardRetryTokenBucket>(strategy.config.tokenBucket)
        assertEquals(5, tokenBucket.config.retryCost)
        assertEquals(10, tokenBucket.config.timeoutRetryCost)
    }

    @Test
    fun testFlagOnUsesStandardDefaults() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy()
        val delayer = assertIs<ExponentialBackoffWithJitter>(strategy.config.delayProvider)
        assertEquals(50.milliseconds, delayer.config.initialDelay)
        assertEquals(2.0, delayer.config.scaleFactor)
        assertEquals(1.seconds, delayer.config.throttlingBaseDelay)
        assertEquals(5.seconds, delayer.config.retryAfterMaxOvershoot)
        val tokenBucket = assertIs<StandardRetryTokenBucket>(strategy.config.tokenBucket)
        assertEquals(14, tokenBucket.config.retryCost)
        assertEquals(5, tokenBucket.config.timeoutRetryCost)
    }

    @Test
    fun testFlagOffTransientUsesTimeoutRetryCost() = runTest {
        val bucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config {})
        val token = bucket.acquireToken()
        token.scheduleRetry(RetryErrorType.Transient)
        assertEquals(500 - 10, bucket.capacity)
    }

    @Test
    fun testFlagOnTransientUsesRetryCost() = runTest {
        System.setProperty(sysPropKey, "true")
        val bucket = StandardRetryTokenBucket(StandardRetryTokenBucket.Config {})
        val token = bucket.acquireToken()
        token.scheduleRetry(RetryErrorType.Transient)
        assertEquals(500 - 14, bucket.capacity)
    }
}
