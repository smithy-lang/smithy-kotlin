/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.middleware

import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.middleware.FlexibleChecksumsResponseMiddleware.Companion.ExpectedResponseChecksum
import aws.smithy.kotlin.runtime.http.middleware.FlexibleChecksumsResponseMiddleware.Companion.ResponseChecksum
import aws.smithy.kotlin.runtime.http.operation.execute
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FlexibleChecksumsResponseMiddlewareTest {

    private fun getMockClient(response: ByteArray, responseHeaders: Headers = Headers.Empty): SdkHttpClient {
        val mockEngine = object : HttpClientEngineBase("test") {
            override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
                val body = object : HttpBody.SourceContent() {
                    override val contentLength: Long = response.size.toLong()
                    override fun readFrom(): SdkSource = response.source()
                    override val isOneShot: Boolean get() = false
                }

                val resp = HttpResponse(HttpStatusCode.OK, responseHeaders, body)

                return HttpCall(request, resp, Instant.now(), Instant.now())
            }
        }
        return sdkHttpClient(mockEngine)
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "crc32c,wS3hug==",
            "crc32,UClbrQ==",
            "sha1,vwFegy8gsWrablgsmDmpvWqf1Yw=",
            "sha256,Z7AuR1ssOIhqbjhaKBn3S0hvPhIm27zu9jqT/1SMjNY=",
        ],
    )
    fun testResponseChecksumValid(checksumAlgorithmName: String, expectedChecksum: String) = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(FlexibleChecksumsResponseMiddleware())

        val response = "abc".repeat(1024).toByteArray()

        val responseHeaders = Headers {
            append("x-amz-checksum-$checksumAlgorithmName", expectedChecksum)
        }

        val client = getMockClient(response, responseHeaders)

        op.execute(client, Unit) {
            op.context[ResponseChecksum] = CompletableDeferred(expectedChecksum)
        }
    }

    @ParameterizedTest
    @CsvSource(
        value = [
            "crc32c,wS3hug==",
            "crc32,UClbrQ==",
            "sha1,vwFegy8gsWrablgsmDmpvWqf1Yw=",
            "sha256,Z7AuR1ssOIhqbjhaKBn3S0hvPhIm27zu9jqT/1SMjNY=",
        ],
    )
    fun testResponseChecksumInvalid(checksumAlgorithmName: String, expectedChecksum: String) = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(FlexibleChecksumsResponseMiddleware())

        val response = "abc".repeat(1024).toByteArray()
        val responseHeaders = Headers {
            append("x-amz-checksum-$checksumAlgorithmName", expectedChecksum)
        }

        val client = getMockClient(response, responseHeaders)

        val incorrectChecksum = "incorrect-checksum-$checksumAlgorithmName"

        val ex = assertFailsWith<RuntimeException> {
            op.execute(client, Unit) {
                op.context[ResponseChecksum] = CompletableDeferred(incorrectChecksum)
            }
        }
        assertContains(ex.message!!, "$incorrectChecksum != $expectedChecksum")
    }

    @Test
    fun testMultipleChecksumsReturned() = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(FlexibleChecksumsResponseMiddleware())

        val response = "abc".repeat(1024).toByteArray()
        val responseHeaders = Headers {
            append("x-amz-checksum-crc32c", "wS3hug==")
            append("x-amz-checksum-sha1", "vwFegy8gsWrablgsmDmpvWqf1Yw=")
            append("x-amz-checksum-crc32", "UClbrQ==")
        }

        val client = getMockClient(response, responseHeaders)

        op.execute(client, Unit) {
            // CRC32C validation should be prioritized
            op.context[ResponseChecksum] = CompletableDeferred("wS3hug==")
        }
    }

    @Test
    fun testSkipsValidationOfMultipartChecksum() = runTest {
        val req = HttpRequestBuilder()
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.install(FlexibleChecksumsResponseMiddleware())

        val response = "abc".repeat(1024).toByteArray()
        val responseHeaders = Headers {
            append("x-amz-checksum-crc32c-1", "wS3hug==")
        }

        val client = getMockClient(response, responseHeaders)

        op.execute(client, Unit) {
            assertNull(op.context.getOrNull(ResponseChecksum))
            assertNull(op.context.getOrNull(ExpectedResponseChecksum))
        }
    }
}
