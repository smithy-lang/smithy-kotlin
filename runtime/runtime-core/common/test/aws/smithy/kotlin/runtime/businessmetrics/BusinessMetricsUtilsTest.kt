/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.businessmetrics

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BusinessMetricsUtilsTest {
    @Test
    fun emitBusinessMetric() {
        val executionContext = ExecutionContext()
        executionContext.emitBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION)

        assertTrue(executionContext.attributes.contains(BusinessMetrics))
        assertTrue(executionContext.attributes[BusinessMetrics].contains(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION))
    }

    @Test
    fun emitMultipleBusinessMetrics() {
        val executionContext = ExecutionContext()
        executionContext.emitBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION)
        executionContext.emitBusinessMetric(SmithyBusinessMetric.SIGV4A_SIGNING)

        assertTrue(executionContext.attributes.contains(BusinessMetrics))
        assertTrue(executionContext.attributes[BusinessMetrics].contains(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION))
        assertTrue(executionContext.attributes[BusinessMetrics].contains(SmithyBusinessMetric.SIGV4A_SIGNING))
    }

    @Test
    fun removeBusinessMetric() {
        val executionContext = ExecutionContext()
        executionContext.emitBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION)

        assertTrue(executionContext.attributes.contains(BusinessMetrics))
        assertTrue(executionContext.attributes[BusinessMetrics].contains(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION))

        executionContext.removeBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION)

        assertTrue(executionContext.attributes.contains(BusinessMetrics))
        assertFalse(executionContext.attributes[BusinessMetrics].contains(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION))
    }

    @Test
    fun containsBusinessMetric() {
        val executionContext = ExecutionContext()

        executionContext.emitBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION)
        assertTrue(executionContext.containsBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION))

        executionContext.removeBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION)
        assertFalse(executionContext.containsBusinessMetric(SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION))
    }

    @Test
    fun businessMetricToString() {
        val businessMetricToString = SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION.toString()
        val businessMetricIdentifier = SmithyBusinessMetric.GZIP_REQUEST_COMPRESSION.identifier

        assertEquals(businessMetricIdentifier, businessMetricToString)
    }

    private suspend fun scopedMetrics() = currentCoroutineContext()[ScopedBusinessMetricsContext]?.metrics

    @Test
    fun noScopedBusinessMetrics() = runTest {
        assertNull(scopedMetrics())
    }

    @Test
    fun withScopedBusinessMetrics() = runTest {
        withScopedBusinessMetrics(SmithyBusinessMetric.WAITER) {
            assertEquals(setOf(SmithyBusinessMetric.WAITER), scopedMetrics())
        }
        assertNull(scopedMetrics())
    }

    @Test
    fun withMultipleScopedBusinessMetrics() = runTest {
        withScopedBusinessMetrics(SmithyBusinessMetric.WAITER, SmithyBusinessMetric.PAGINATOR) {
            assertEquals(setOf(SmithyBusinessMetric.WAITER, SmithyBusinessMetric.PAGINATOR), scopedMetrics())
        }
    }

    @Test
    fun nestedWithScopedBusinessMetrics() = runTest {
        withScopedBusinessMetrics(SmithyBusinessMetric.WAITER) {
            withScopedBusinessMetrics(SmithyBusinessMetric.PAGINATOR) {
                assertEquals(setOf(SmithyBusinessMetric.WAITER, SmithyBusinessMetric.PAGINATOR), scopedMetrics())
            }
            assertEquals(setOf(SmithyBusinessMetric.WAITER), scopedMetrics())
        }
    }

    @Test
    fun withScopedBusinessMetricsReturnsResult() = runTest {
        assertEquals(42, withScopedBusinessMetrics(SmithyBusinessMetric.WAITER) { 42 })
    }
}
