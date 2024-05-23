/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.businessmetrics

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlin.test.Test
import kotlin.test.assertTrue

class BusinessMetricsUtilsTest {
    @Test
    fun emitBusinessMetric() {
        val executionContext = ExecutionContext()
        executionContext.emitBusinessMetric(BusinessMetrics.GZIP_REQUEST_COMPRESSION)

        assertTrue(executionContext.attributes.contains(businessMetrics))
        assertTrue(executionContext.attributes[businessMetrics].contains(BusinessMetrics.GZIP_REQUEST_COMPRESSION.identifier))
    }

    @Test
    fun emitMultipleBusinessMetrics() {
        val executionContext = ExecutionContext()
        executionContext.emitBusinessMetric(BusinessMetrics.GZIP_REQUEST_COMPRESSION)
        executionContext.emitBusinessMetric(BusinessMetrics.S3_EXPRESS_BUCKET)

        assertTrue(executionContext.attributes.contains(businessMetrics))
        assertTrue(executionContext.attributes[businessMetrics].contains(BusinessMetrics.GZIP_REQUEST_COMPRESSION.identifier))
        assertTrue(executionContext.attributes[businessMetrics].contains(BusinessMetrics.S3_EXPRESS_BUCKET.identifier))
    }
}
