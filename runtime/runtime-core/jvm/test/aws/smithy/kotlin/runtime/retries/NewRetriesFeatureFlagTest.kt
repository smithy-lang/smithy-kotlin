/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.delay.ExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class NewRetriesFeatureFlagTest {
    private val sysPropKey = "smithy.newRetries2026"

    @AfterTest
    fun cleanup() {
        System.clearProperty(sysPropKey)
    }

    @Test
    fun testFlagOffUsesLegacyDefaults() {
        System.clearProperty(sysPropKey)
        val strategy = StandardRetryStrategy()
        val delayer = assertIs<ExponentialBackoffWithJitter>(strategy.config.delayProvider)
        assertEquals(10.milliseconds, delayer.config.initialDelay)
        assertEquals(1.5, delayer.config.scaleFactor)
    }

    @Test
    fun testFlagOnUsesStandardDefaults() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy()
        val delayer = assertIs<ExponentialBackoffWithJitter>(strategy.config.delayProvider)
        assertEquals(50.milliseconds, delayer.config.initialDelay)
        assertEquals(2.0, delayer.config.scaleFactor)
    }

    @Test
    fun testFlagOnUsesStandardTokenBucket() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy()
        assertIs<StandardRetryTokenBucket>(strategy.config.tokenBucket)
    }
}
