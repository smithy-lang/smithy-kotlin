/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ExperimentalApi
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpCall
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.EngineAttributes
import aws.smithy.kotlin.runtime.http.operation.HttpDeserializer
import aws.smithy.kotlin.runtime.http.operation.HttpSerializer
import aws.smithy.kotlin.runtime.http.operation.OperationMetrics
import aws.smithy.kotlin.runtime.http.operation.SdkHttpOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.operation.telemetry
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.http.util.MetricRecord
import aws.smithy.kotlin.runtime.http.util.RecordingTelemetryProvider
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.telemetry.context.Context
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

private const val SERVICE_NAME = "TestService"
private const val OPERATION_NAME = "TestOperation"

private val RPC_SERVICE_KEY = AttributeKey<String>("rpc.service")
private val RPC_METHOD_KEY = AttributeKey<String>("rpc.method")

private val REQUEST_BODY = "request-payload".encodeToByteArray()
private val RESPONSE_BODY = "response-payload-that-is-longer".encodeToByteArray()

private object TelemetryTestInput
private data class TelemetryTestOutput(val body: HttpBody)

@OptIn(ExperimentalApi::class)
class OperationTelemetryInterceptorTest {
    private fun runOperation(provider: RecordingTelemetryProvider, withTimeToFirstByte: Boolean) = runTest {
        val op = SdkHttpOperation.build<TelemetryTestInput, TelemetryTestOutput> {
            serializeWith = object : HttpSerializer.NonStreaming<TelemetryTestInput> {
                override fun serialize(
                    context: ExecutionContext,
                    input: TelemetryTestInput,
                ): HttpRequestBuilder = HttpRequestBuilder().apply {
                    body = HttpBody.fromBytes(REQUEST_BODY)
                }
            }

            deserializeWith = object : HttpDeserializer.NonStreaming<TelemetryTestOutput> {
                override fun deserialize(
                    context: ExecutionContext,
                    call: HttpCall,
                    payload: ByteArray?,
                ): TelemetryTestOutput = TelemetryTestOutput(call.response.body)
            }

            operationName = OPERATION_NAME
            serviceName = SERVICE_NAME

            telemetry {
                this.provider = provider
                metrics = OperationMetrics(SERVICE_NAME, provider)
            }
        }

        if (withTimeToFirstByte) {
            // simulate the engine reporting time-to-first-byte so the attempt-overhead metric is emitted
            op.context[EngineAttributes.TimeToFirstByte] = 1.milliseconds
        }

        val engine = TestEngine { _, request ->
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.fromBytes(RESPONSE_BODY))
            HttpCall(request, resp, Instant.now(), Instant.now())
        }

        op.roundTrip(SdkHttpClient(engine), TelemetryTestInput)
    }

    private fun assertHasRpcAttributesAndContext(record: MetricRecord, expectedContext: Context) {
        assertEquals(SERVICE_NAME, record.attributes.getOrNull(RPC_SERVICE_KEY), "${record.name} missing rpc.service")
        assertEquals(OPERATION_NAME, record.attributes.getOrNull(RPC_METHOD_KEY), "${record.name} missing rpc.method")
        assertNotNull(record.context, "${record.name} missing telemetry context")
        assertSame(expectedContext, record.context, "${record.name} did not carry the current telemetry context")
    }

    @Test
    fun testRequestPayloadSizeCarriesAttributesAndContext() {
        val provider = RecordingTelemetryProvider()
        runOperation(provider, withTimeToFirstByte = false)

        val records = provider.recordsFor("smithy.client.call.request_payload_size")
        assertEquals(1, records.size, "expected exactly one request payload size measurement")
        val record = records.single()
        assertHasRpcAttributesAndContext(record, provider.activeContext)
        assertEquals(REQUEST_BODY.size.toDouble(), record.value)
    }

    @Test
    fun testResponsePayloadSizeCarriesAttributesAndContext() {
        val provider = RecordingTelemetryProvider()
        runOperation(provider, withTimeToFirstByte = false)

        val records = provider.recordsFor("smithy.client.call.response_payload_size")
        assertEquals(1, records.size, "expected exactly one response payload size measurement")
        val record = records.single()
        assertHasRpcAttributesAndContext(record, provider.activeContext)
        assertEquals(RESPONSE_BODY.size.toDouble(), record.value)
    }

    @Test
    fun testAttemptOverheadDurationCarriesAttributesAndContext() {
        val provider = RecordingTelemetryProvider()
        runOperation(provider, withTimeToFirstByte = true)

        val records = provider.recordsFor("smithy.client.call.attempt_overhead_duration")
        assertEquals(1, records.size, "expected exactly one attempt overhead measurement")
        assertHasRpcAttributesAndContext(records.single(), provider.activeContext)
    }

    @Test
    fun testAttemptOverheadDurationNotEmittedWithoutTimeToFirstByte() {
        val provider = RecordingTelemetryProvider()
        runOperation(provider, withTimeToFirstByte = false)

        assertTrue(
            provider.recordsFor("smithy.client.call.attempt_overhead_duration").isEmpty(),
            "attempt overhead metric should not be emitted without a time-to-first-byte measurement",
        )
    }

    @Test
    fun testAllEmittedMetricsCarryRpcAttributes() {
        // regression guard for the bug: every operation metric should carry rpc.service/rpc.method
        val provider = RecordingTelemetryProvider()
        runOperation(provider, withTimeToFirstByte = true)

        assertTrue(provider.records.isNotEmpty(), "expected the operation to emit metrics")
        provider.records.forEach { record ->
            assertEquals(SERVICE_NAME, record.attributes.get(RPC_SERVICE_KEY), "${record.name} missing rpc.service")
            assertEquals(OPERATION_NAME, record.attributes.get(RPC_METHOD_KEY), "${record.name} missing rpc.method")
        }
    }
}
