/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.IgnoreNative
import aws.smithy.kotlin.runtime.collections.get
import aws.smithy.kotlin.runtime.hashing.Crc32
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.text.encoding.encodeBase64String
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpChecksumRequiredInterceptorTest {
    private val client = SdkHttpClient(TestEngine())

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun itSetsContentMd5Header() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.context.attributes[HttpOperationContext.DefaultChecksumAlgorithm] = "MD5"
        op.interceptors.add(
            HttpChecksumRequiredInterceptor(),
        )

        val expected = "RG22oBSZFmabBbkzVGRi4w=="
        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(expected, call.request.headers["Content-MD5"])
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun itSetsContentCrc32Header() = runTest {
        val testBody = "<Foo>bar</Foo>".encodeToByteArray()

        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes(testBody)
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.context.attributes[HttpOperationContext.DefaultChecksumAlgorithm] = "CRC32"
        op.interceptors.add(
            HttpChecksumRequiredInterceptor(),
        )

        val crc32 = Crc32()
        crc32.update(testBody)
        val expected = crc32.digest().encodeBase64String()

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(expected, call.request.headers["x-amz-checksum-crc32"])
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun itSetsHeaderForNonBytesContent() = runTest {
        val req = HttpRequestBuilder().apply {
            body = object : HttpBody.ChannelContent() {
                override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel("fooey".encodeToByteArray())
            }
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.context.attributes[HttpOperationContext.DefaultChecksumAlgorithm] = "MD5"
        op.interceptors.add(
            HttpChecksumRequiredInterceptor(),
        )

        val expected = "vJLiaOiNxaxdWfYAYzdzFQ=="
        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals(expected, call.request.headers["Content-MD5"])
    }

    @IgnoreNative // FIXME Re-enable after Kotlin/Native Implementation
    @Test
    fun itDoesNotSetContentMd5Header() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            HttpChecksumRequiredInterceptor(),
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertNull(call.request.headers["Content-MD5"])
    }
}
