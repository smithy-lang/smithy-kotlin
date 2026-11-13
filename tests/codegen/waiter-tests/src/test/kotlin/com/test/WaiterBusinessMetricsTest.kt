/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.test

import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.ScopedBusinessMetricsContext
import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
import aws.smithy.kotlin.runtime.retries.Outcome
import aws.smithy.kotlin.runtime.retries.RetryStrategy
import aws.smithy.kotlin.runtime.retries.StandardRetryStrategy
import aws.smithy.kotlin.runtime.retries.policy.RetryPolicy
import com.test.model.EntityLists
import com.test.model.GetStringEqualsRequest
import com.test.model.GetStringEqualsResponse
import com.test.waiters.waitUntilStringListAnyStringEquals
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaiterBusinessMetricsTest {
    private class RecordingWaitersTestClient(
        private val delegate: DefaultWaitersTestClient<GetStringEqualsResponse>,
    ) : WaitersTestClient by delegate {
        val observedMetrics = mutableListOf<Set<BusinessMetric>?>()

        override suspend fun getStringEquals(input: GetStringEqualsRequest): GetStringEqualsResponse {
            observedMetrics += coroutineContext[ScopedBusinessMetricsContext]?.metrics
            return delegate.getStringEquals(input)
        }
    }

    private val responses = listOf(
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("bar") } },
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("bar") } },
        GetStringEqualsResponse { lists = EntityLists { strings = listOf("foo") } },
    )

    private fun newClient() = RecordingWaitersTestClient(DefaultWaitersTestClient(responses.map { Result.success(it) }))

    @Test
    fun testWaiterEmitsBusinessMetric() = runTest {
        val client = newClient()
        client.waitUntilStringListAnyStringEquals(GetStringEqualsRequest { name = "test" })

        assertEquals(responses.size, client.observedMetrics.size)
        client.observedMetrics.forEach { assertEquals(setOf(SmithyBusinessMetric.WAITER), it) }
    }

    @Test
    fun testRetryStrategyCallsDoNotEmitBusinessMetric() = runTest {
        val client = newClient()
        val strategyObservedMetrics = mutableListOf<Set<BusinessMetric>?>()

        // A custom strategy which makes its own call before delegating each attempt
        val strategy = object : RetryStrategy by StandardRetryStrategy() {
            override suspend fun <R> retry(policy: RetryPolicy<R>, block: suspend () -> R): Outcome<R> = StandardRetryStrategy().retry(policy) {
                strategyObservedMetrics += currentCoroutineContext()[ScopedBusinessMetricsContext]?.metrics
                block()
            }
        }

        client.waitUntilStringListAnyStringEquals(GetStringEqualsRequest { name = "test" }, strategy)

        assertEquals(responses.size, strategyObservedMetrics.size)
        strategyObservedMetrics.forEach { assertNull(it) }
        client.observedMetrics.forEach { assertEquals(setOf(SmithyBusinessMetric.WAITER), it) }
    }

    @Test
    fun testDirectCallDoesNotEmitBusinessMetric() = runTest {
        val client = newClient()
        client.getStringEquals(GetStringEqualsRequest { name = "test" })

        assertEquals(listOf<Set<BusinessMetric>?>(null), client.observedMetrics)
    }
}
