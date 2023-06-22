/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.telemetry.logging.loggingContext
import aws.smithy.kotlin.runtime.telemetry.trace.traceSpan
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
class SdkHttpOperationTest {
    @Test
    fun testTelemetryInstrumentation() = runTest {
        val op = newTestOperation<Unit, Unit>(HttpRequestBuilder(), Unit)
        val client = SdkHttpClient(TestEngine())
        op.execute(client, Unit) {
            val cc = kotlin.coroutines.coroutineContext
            // test certain elements are setup correctly
            assertEquals("TestService.TestOperation", cc.loggingContext["rpc"])
            assertNotNull(cc.loggingContext["sdkInvocationId"])
            assertNotNull(cc.traceSpan)
        }
    }
}
