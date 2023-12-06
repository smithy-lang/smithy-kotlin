/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.compression.CompressionAlgorithm
import aws.smithy.kotlin.runtime.http.compression.Gzip
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.source
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

expect fun decompressGzipBytes(compressed: ByteArray): ByteArray

class RequestCompressionInterceptorTest {

    private val client = SdkHttpClient(TestEngine())

    private suspend fun mockCall(
        suppliedBody: HttpBody,
        compressionThresholdBytes: Long,
        supportedCompressionAlgorithms: List<String>,
        availableCompressionAlgorithms: List<CompressionAlgorithm>,
        additionalHeaders: Headers = Headers.Empty,
    ): HttpCall {
        val request = HttpRequestBuilder().apply {
            body = suppliedBody
        }

        request.headers.appendAll(additionalHeaders)

        val op = newTestOperation<Unit, Unit>(
            request,
            Unit,
        )

        op.interceptors.add(
            RequestCompressionInterceptor(
                compressionThresholdBytes,
                availableCompressionAlgorithms,
                supportedCompressionAlgorithms,
            ),
        )

        op.roundTrip(client, Unit)

        return op.context.attributes[HttpOperationContext.HttpCallList].first()
    }

    @Test
    fun testCompressionThresholdTooHigh() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val call = mockCall(
            HttpBody.fromBytes(bytes),
            bytes.size + 1L,
            listOf("gzip"),
            listOf(Gzip()),
        )

        val contentEncodingHeader = call.request.headers["Content-Encoding"]
        assertEquals(null, contentEncodingHeader)

        val sentBytes = call.request.body.readAll()
        assertEquals(bytes, sentBytes)
    }

    @Test
    fun testCompression() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val call = mockCall(
            HttpBody.fromBytes(bytes),
            bytes.size.toLong(),
            listOf("gzip"),
            listOf(Gzip()),
        )

        val contentEncodingHeader = call.request.headers.getAll("Content-Encoding")
        assertEquals(listOf("gzip"), contentEncodingHeader)

        val compressedByes = call.request.body.readAll()
        val decompressedBytes = compressedByes?.let { decompressGzipBytes(compressedByes) }
        assertContentEquals(bytes, decompressedBytes)
    }

    @Test
    fun testSdkSource() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val call = mockCall(
            bytes.source().toHttpBody(),
            bytes.size + 1L, // Compression threshold bytes will be ignored
            listOf("gzip"),
            listOf(Gzip()),
        )

        val contentEncodingHeader = call.request.headers.getAll("Content-Encoding")
        assertEquals(listOf("gzip"), contentEncodingHeader)

        val compressedByes = call.request.body.readAll()
        val decompressedBytes = compressedByes?.let { decompressGzipBytes(compressedByes) }
        assertContentEquals(bytes, decompressedBytes)
    }

    @Test
    fun testSdkByteReadChannel() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val call = mockCall(
            SdkByteReadChannel(bytes).toHttpBody(),
            bytes.size + 1L, // Compression threshold bytes will be ignored
            listOf("gzip"),
            listOf(Gzip()),
        )

        val contentEncodingHeader = call.request.headers.getAll("Content-Encoding")
        assertEquals(listOf("gzip"), contentEncodingHeader)

        val compressedByes = call.request.body.readAll()
        val decompressedBytes = compressedByes?.let { decompressGzipBytes(compressedByes) }
        assertContentEquals(bytes, decompressedBytes)
    }

    @Test
    fun testHeaderAlreadySet() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val call = mockCall(
            HttpBody.fromBytes(bytes),
            bytes.size.toLong(),
            listOf("gzip"),
            listOf(Gzip()),
            Headers { set("Content-Encoding", "br") },
        )

        val contentEncodingHeader = call.request.headers.getAll("Content-Encoding")
        assertEquals(listOf("br", "gzip"), contentEncodingHeader)

        val compressedByes = call.request.body.readAll()
        val decompressedBytes = compressedByes?.let { decompressGzipBytes(compressedByes) }
        assertContentEquals(bytes, decompressedBytes)
    }

    @Test
    fun testNoSupportedAlgorithms() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val call = mockCall(
            HttpBody.fromBytes(bytes),
            bytes.size.toLong(),
            listOf(),
            listOf(),
        )

        val contentEncodingHeader = call.request.headers["Content-Encoding"]
        assertEquals(null, contentEncodingHeader)

        val sentBytes = call.request.body.readAll()
        assertEquals(bytes, sentBytes)
    }

    @Test
    fun testInvalidCompressionThreshold() = runTest {
        val payload = "<Foo>bar</Foo>"
        val bytes = payload.encodeToByteArray()

        val op = newTestOperation<Unit, Unit>(
            HttpRequestBuilder().apply {
                body = HttpBody.fromBytes(bytes)
            },
            Unit,
        )

        val invalidCompressionThreshold = -1L

        assertFailsWith<IllegalArgumentException> {
            op.interceptors.add(
                RequestCompressionInterceptor(
                    invalidCompressionThreshold,
                    listOf(Gzip()),
                    listOf("gzip"),
                ),
            )
        }
    }
}
