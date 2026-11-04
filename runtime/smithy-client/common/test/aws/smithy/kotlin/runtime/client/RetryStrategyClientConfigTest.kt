/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.client

import aws.smithy.kotlin.runtime.retries.AdaptiveRetryStrategy
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RetryStrategyClientConfigTest {
    @Test
    fun testDslDefaultsToStandard() {
        val builder = RetryStrategyClientConfigImpl.BuilderImpl()
        builder.retryStrategy { maxAttempts = 2 }

        val strategy = builder.buildRetryStrategyClientConfig().retryStrategy
        assertIs<StandardRetryStrategy>(strategy)
        assertTrue(strategy !is AdaptiveRetryStrategy)
        assertEquals(2, strategy.config.maxAttempts)
    }

    @Test
    fun testDslKeepsAdaptiveStrategy() {
        val builder = RetryStrategyClientConfigImpl.BuilderImpl()
        builder.retryStrategy = AdaptiveRetryStrategy { maxAttempts = 5 }
        builder.retryStrategy { maxAttempts = 2 }

        val strategy = assertIs<AdaptiveRetryStrategy>(builder.buildRetryStrategyClientConfig().retryStrategy)
        assertEquals(2, strategy.config.maxAttempts)
    }

    @Test
    fun testDslKeepsStandardStrategySettings() {
        val builder = RetryStrategyClientConfigImpl.BuilderImpl()
        builder.retryStrategy = StandardRetryStrategy {
            maxAttempts = 5
            enableLongPollingBackoff = false
        }
        builder.retryStrategy { maxAttempts = 2 }

        val strategy = assertIs<StandardRetryStrategy>(builder.buildRetryStrategyClientConfig().retryStrategy)
        assertTrue(strategy !is AdaptiveRetryStrategy)
        assertEquals(2, strategy.config.maxAttempts)
        assertEquals(false, strategy.config.enableLongPollingBackoff)
    }
}
