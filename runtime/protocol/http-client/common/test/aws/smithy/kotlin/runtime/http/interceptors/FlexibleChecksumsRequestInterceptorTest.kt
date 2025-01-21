/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.ClientException
import aws.smithy.kotlin.runtime.IgnoreNative
import aws.smithy.kotlin.runtime.client.config.RequestHttpChecksumConfig
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

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun itSetsChecksumHeader() = runTest {
        checksums.forEach { (checksumAlgorithmName, expectedChecksumValue) ->
            val req = HttpRequestBuilder().apply {
                body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
            }

            val op = newTestOperation<Unit, Unit>(req, Unit)

            op.interceptors.add(
                FlexibleChecksumsRequestInterceptor(
                    requestChecksumAlgorithm = checksumAlgorithmName,
                    requestChecksumRequired = true,
                    requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_SUPPORTED,
                ),
            )

            op.roundTrip(client, Unit)
            val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
            assertEquals(expectedChecksumValue, call.request.headers["x-amz-checksum-$checksumAlgorithmName"])
        }
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
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

        op.context.attributes[HttpOperationContext.DefaultChecksumAlgorithm] = "CRC32"
        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor(
                requestChecksumAlgorithm = checksumAlgorithmName,
                requestChecksumRequired = true,
                requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_SUPPORTED,
            ),
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        assertEquals(1, call.request.headers.getNumChecksumHeaders())
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun itThrowsOnUnsupportedChecksumAlgorithm() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val unsupportedChecksumAlgorithmName = "fooblefabble1024"

        val op = newTestOperation<Unit, Unit>(req, Unit)

        assertFailsWith<ClientException> {
            op.interceptors.add(
                FlexibleChecksumsRequestInterceptor(
                    requestChecksumAlgorithm = unsupportedChecksumAlgorithmName,
                    requestChecksumRequired = true,
                    requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_SUPPORTED,
                ),
            )
            op.roundTrip(client, Unit)
        }
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
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
            FlexibleChecksumsRequestInterceptor(
                requestChecksumAlgorithm = checksumAlgorithmName,
                requestChecksumRequired = true,
                requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_SUPPORTED,
            ),
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        assertEquals(0, call.request.headers.getNumChecksumHeaders())
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun testCompletingSource() = runTest {
        val hashFunctionName = "crc32"

        val byteArray = ByteArray(19456) { 0xf }
        val source = byteArray.source()
        val completableDeferred = CompletableDeferred<String>()
        val hashingSource = HashingSource(hashFunctionName.toHashFunction()!!, source)
        val completingSource = CompletingSource(completableDeferred, hashingSource)

        completingSource.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted) // deferred value should not be completed because the source is not exhausted
        completingSource.readToByteArray() // source is now exhausted

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.await())
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun testCompletingByteReadChannel() = runTest {
        val hashFunctionName = "sha256"

        val byteArray = ByteArray(2143) { 0xf }
        val channel = SdkByteReadChannel(byteArray)
        val completableDeferred = CompletableDeferred<String>()
        val hashingChannel = HashingByteReadChannel(hashFunctionName.toHashFunction()!!, channel)
        val completingChannel =
            CompletingByteReadChannel(completableDeferred, hashingChannel)

        completingChannel.read(SdkBuffer(), 1L)
        assertFalse(completableDeferred.isCompleted)

        completingChannel.readAll(SdkBuffer())

        val expectedHash = hashFunctionName.toHashFunction()!!
        expectedHash.update(byteArray)

        assertTrue(completableDeferred.isCompleted)
        assertEquals(expectedHash.digest().encodeBase64String(), completableDeferred.await())
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
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
            FlexibleChecksumsRequestInterceptor(
                requestChecksumAlgorithm = checksumAlgorithmName,
                requestChecksumRequired = true,
                requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_SUPPORTED,
            ),
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        assertEquals(1, call.request.headers.getNumChecksumHeaders())
        assertEquals(precalculatedChecksumValue, call.request.headers["x-amz-checksum-sha256"])
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun testDefaultChecksumConfiguration() = runTest {
        setOf(
            DefaultChecksumTest(true, RequestHttpChecksumConfig.WHEN_SUPPORTED, true),
            DefaultChecksumTest(true, RequestHttpChecksumConfig.WHEN_REQUIRED, true),
            DefaultChecksumTest(false, RequestHttpChecksumConfig.WHEN_SUPPORTED, true),
            DefaultChecksumTest(false, RequestHttpChecksumConfig.WHEN_REQUIRED, false),
        ).forEach { runDefaultChecksumTest(it) }
    }

    private fun Headers.getNumChecksumHeaders(): Int = entries().count { (name, _) -> name.startsWith("x-amz-checksum-") }

    private data class DefaultChecksumTest(
        val requestChecksumRequired: Boolean,
        val requestChecksumCalculation: RequestHttpChecksumConfig,
        val defaultChecksumExpected: Boolean,
    )

    private fun runDefaultChecksumTest(
        testCase: DefaultChecksumTest,
    ) = runTest {
        val defaultChecksumAlgorithmName = "crc32"
        val expectedChecksumValue = "WdqXHQ=="

        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.context.attributes[HttpOperationContext.DefaultChecksumAlgorithm] = "CRC32"
        op.interceptors.add(
            FlexibleChecksumsRequestInterceptor(
                requestChecksumAlgorithm = null, // See if default checksum is applied
                requestChecksumRequired = testCase.requestChecksumRequired,
                requestChecksumCalculation = testCase.requestChecksumCalculation,
            ),
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()

        when (testCase.defaultChecksumExpected) {
            true -> assertEquals(expectedChecksumValue, call.request.headers["x-amz-checksum-$defaultChecksumAlgorithmName"])
            false -> assertFalse { call.request.headers.contains("x-amz-checksum-$defaultChecksumAlgorithmName") }
        }
    }
}
