/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.delay.ExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.StandardExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.StandardRetryTokenBucket
import kotlin.test.*

class NewRetriesFeatureFlagTest {
    private val sysPropKey = "smithy.newRetries2026"

    @AfterTest
    fun cleanup() {
        System.clearProperty(sysPropKey)
    }

    @Test
    fun testFlagOffUsesLegacyDelayProvider() {
        System.clearProperty(sysPropKey)
        val strategy = StandardRetryStrategy()
        assertIs<ExponentialBackoffWithJitter>(strategy.config.delayProvider)
    }

    @Test
    fun testFlagOnUsesStandardDelayProvider() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy()
        assertIs<StandardExponentialBackoffWithJitter>(strategy.config.delayProvider)
    }

    @Test
    fun testFlagOnUsesStandardTokenBucket() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy()
        assertIs<StandardRetryTokenBucket>(strategy.config.tokenBucket)
    }

}
