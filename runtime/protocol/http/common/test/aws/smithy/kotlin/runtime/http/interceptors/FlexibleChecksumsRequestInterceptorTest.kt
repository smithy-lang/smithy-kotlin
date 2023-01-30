/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.client.ExecutionContext
import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.content.ByteArrayContent
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.encodeBase64String
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class FlexibleChecksumsRequestInterceptorTest {
    private val mockEngine = object : HttpClientEngineBase("test") {
        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall {
            val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
            return HttpCall(request, resp, Instant.now(), Instant.now())
        }
    }
    private val client = SdkHttpClient(mockEngine)

    @ParameterizedTest
    @CsvSource(
        value = [
            "crc32c,6QF4+w==",
            "crc32,WdqXHQ==",
            "sha1,Vk45UfsxIxsZQQ3D1gAU7PsGvz4=",
            "sha256,1dXchshIKqXiaKCqueqR7AOz1qLpiqayo7gbnaxzaQo=",
        ],
    )
    fun itSetsChecksumHeader(checksumAlgorithmName: String, expectedChecksumValue: String) = runTest {
        val req = HttpRequestBuilder().apply {
            body = ByteArrayContent("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor<Unit> {
                checksumAlgorithmName
            },
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(expectedChecksumValue, call.request.headers["x-amz-checksum-$checksumAlgorithmName"])
    }

    @Test
    fun itAllowsOnlyOneChecksumHeader() = runTest {
        val req = HttpRequestBuilder().apply {
            body = ByteArrayContent("<Foo>bar</Foo>".encodeToByteArray())
        }
        req.headers { append("x-amz-checksum-sha256", "sha256-checksum-value") }
        req.headers { append("x-amz-checksum-crc32", "crc32-checksum-value") }
        req.headers { append("x-amz-checksum-sha1", "sha1-checksum-value") }

        val checksumAlgorithmName = "crc32c"

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor<Unit> {
                checksumAlgorithmName
            },
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        assertEquals(1, call.request.headers.getNumChecksumHeaders())
    }

    @Test
    fun itThrowsOnUnsupportedChecksumAlgorithm() = runTest {
        val req = HttpRequestBuilder().apply {
            body = ByteArrayContent("<Foo>bar</Foo>".encodeToByteArray())
        }

        val unsupportedChecksumAlgorithmName = "fooblefabble1024"

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor<Unit> {
                unsupportedChecksumAlgorithmName
            },
        )

        assertFailsWith<ClientException> {
            op.roundTrip(client, Unit)
        }
    }

    @Test
    fun itRemovesChecksumHeadersForAwsChunked() = runTest {
        val req = HttpRequestBuilder().apply {
            body = object : HttpBody.SourceContent() {
                override val contentLength: Long = 1024 * 1024 * 128
                override fun readFrom(): SdkSource = "a".repeat(contentLength.toInt()).toByteArray().source()
                override val isOneShot: Boolean get() = false
            }
        }

        val checksumAlgorithmName = "crc32c"

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor<Unit> {
                checksumAlgorithmName
            },
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        assertEquals(0, call.request.headers.getNumChecksumHeaders())
    }

    @Test
    fun testCompletingSource() = runTest {
        val hashFunctionName = "crc32"

        val byteArray = ByteArray(19456) { 0xf }
        val source = byteArray.source()
        val completableDeferred = CompletableDeferred<String>()
        val hashingSource = HashingSource(hashFunctionName.toHashFunction()!!, source)
        val completingSource = FlexibleChecksumsRequestInterceptor.CompletingSource(completableDeferred, hashingSource)

        completingSource.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted) // deferred value should not be completed because the source is not exhausted
        completingSource.readToByteArray() // source is now exhausted

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.getCompleted())
    }

    @Test
    fun testCompletingByteReadChannel() = runTest {
        val hashFunctionName = "sha256"

        val byteArray = ByteArray(2143) { 0xf }
        val channel = SdkByteReadChannel(byteArray)
        val completableDeferred = CompletableDeferred<String>()
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)
        val completingChannel = FlexibleChecksumsRequestInterceptor.CompletingByteReadChannel(completableDeferred, hashingChannel)

        completingChannel.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted)

        completingChannel.readAll(SdkBuffer())

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.getCompleted())
    }

    private fun Headers.getNumChecksumHeaders(): Long = entries().stream()
        .filter { (name, _) -> name.startsWith("x-amz-checksum-") }
        .count()
}
