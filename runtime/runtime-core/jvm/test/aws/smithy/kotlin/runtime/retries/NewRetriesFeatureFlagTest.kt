/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.retries

import aws.smithy.kotlin.runtime.retries.delay.ExponentialBackoffWithJitter
import aws.smithy.kotlin.runtime.retries.delay.RetryAwareDelayProvider
import aws.smithy.kotlin.runtime.retries.delay.StandardExponentialBackoffWithJitter
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds

class NewRetriesFeatureFlagTest {
    private val sysPropKey = "smithy.newRetries2026"

    @AfterTest
    fun cleanup() {
        System.clearProperty(sysPropKey)
    }

    @Test
    fun testFlagOffUsesOldDefaults() {
        System.clearProperty(sysPropKey)
        val strategy = StandardRetryStrategy()
        assertIs<ExponentialBackoffWithJitter>(strategy.config.delayProvider)
        assertEquals(3, strategy.config.maxAttempts)
    }

    @Test
    fun testFlagOnUsesNewDefaults() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy()
        assertIs<RetryAwareDelayProvider>(strategy.config.delayProvider)
        assertIs<StandardExponentialBackoffWithJitter>(strategy.config.delayProvider)
    }

    @Test
    fun testFlagOnDynamoDbMaxAttempts4() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy { serviceName = "dynamodb" }
        assertEquals(4, strategy.config.maxAttempts)
    }

    @Test
    fun testFlagOnNonDynamoDbMaxAttempts3() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy { serviceName = "s3" }
        assertEquals(3, strategy.config.maxAttempts)
    }

    @Test
    fun testFlagOnDynamoDbStreamsMaxAttempts4() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy { serviceName = "dynamodb streams" }
        assertEquals(4, strategy.config.maxAttempts)
    }

    @Test
    fun testFlagOnDynamoDbStreamsCaseInsensitive() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy { serviceName = "DynamoDB Streams" }
        assertEquals(4, strategy.config.maxAttempts)
    }

    @Test
    fun testFlagOnDynamoDbExplicitMaxAttemptsHonored() {
        System.setProperty(sysPropKey, "true")
        val strategy = StandardRetryStrategy {
            serviceName = "dynamodb"
            maxAttempts = 3
        }
        assertEquals(3, strategy.config.maxAttempts)
    }

    @Test
    fun testFlagOffDynamoDbMaxAttempts3() {
        System.clearProperty(sysPropKey)
        val strategy = StandardRetryStrategy { serviceName = "dynamodb" }
        assertEquals(3, strategy.config.maxAttempts)
    }
}
