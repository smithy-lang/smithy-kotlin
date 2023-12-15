/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.hashing.toHashFunction
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.*
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class FlexibleChecksumsRequestInterceptorTest {
    private val client = SdkHttpClient(TestEngine())

    private val checksums = listOf(
        "crc32c" to "6QF4+w==",
        "crc32" to "WdqXHQ==",
        "sha1" to "Vk45UfsxIxsZQQ3D1gAU7PsGvz4=",
        "sha256" to "1dXchshIKqXiaKCqueqR7AOz1qLpiqayo7gbnaxzaQo=",
    )

    @Test
    fun itSetsChecksumHeader() = runTest {
        checksums.forEach { (checksumAlgorithmName, expectedChecksumValue) ->
            val req = HttpRequestBuilder().apply {
                body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
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
    }

    @Test
    fun itAllowsOnlyOneChecksumHeader() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
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
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
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
        val data = ByteArray(65536 * 32) { 'a'.code.toByte() }

        val req = HttpRequestBuilder().apply {
            body = object : HttpBody.SourceContent() {
                override val contentLength: Long = data.size.toLong()
                override fun readFrom(): SdkSource = data.source()
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
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.await())
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
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.await())
    }

    @Test
    fun itUsesPrecalculatedChecksum() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        val checksumAlgorithmName = "sha256"
        val precalculatedChecksumValue = "sha256-checksum-value"
        req.headers { append("x-amz-checksum-$checksumAlgorithmName", precalculatedChecksumValue) }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor<Unit> {
                checksumAlgorithmName
            },
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        assertEquals(1, call.request.headers.getNumChecksumHeaders())
        assertEquals(precalculatedChecksumValue, call.request.headers["x-amz-checksum-sha256"])
    }

    private fun Headers.getNumChecksumHeaders(): Int = entries().count { (name, _) -> name.startsWith("x-amz-checksum-") }
}
