/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.traits

import aws.smithy.kotlin.runtime.businessmetrics.BusinessMetric
import aws.smithy.kotlin.runtime.businessmetrics.ScopedBusinessMetricsContext
import aws.smithy.kotlin.runtime.businessmetrics.SmithyBusinessMetric
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import smithy.kotlin.traits.model.ListFunctionsRequest
import smithy.kotlin.traits.model.ListFunctionsResponse
import smithy.kotlin.traits.paginators.listFunctionsPaginated
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class PaginatorBusinessMetricsTest {
    private class RecordingLambdaClient(private val delegate: TestLambdaClient = TestLambdaClient()) : LambdaClient by delegate {
        val observedMetrics = mutableListOf<Set<BusinessMetric>?>()

        override suspend fun listFunctions(input: ListFunctionsRequest): ListFunctionsResponse {
            observedMetrics += coroutineContext[ScopedBusinessMetricsContext]?.metrics
            return delegate.listFunctions(input)
        }
    }

    @Test
    fun testPaginatorEmitsBusinessMetric() = runBlocking {
        val delegate = TestLambdaClient().apply { pageCount = 3 }
        val client = RecordingLambdaClient(delegate)
        client.listFunctionsPaginated { }.toList()

        assertEquals(delegate.pageCount, client.observedMetrics.size)
        client.observedMetrics.forEach { assertEquals(setOf(SmithyBusinessMetric.PAGINATOR), it) }
    }

    @Test
    fun testDirectCallDoesNotEmitBusinessMetric() = runBlocking {
        val client = RecordingLambdaClient()
        client.listFunctions(ListFunctionsRequest { })

        assertEquals(listOf<Set<BusinessMetric>?>(null), client.observedMetrics)
    }
}
