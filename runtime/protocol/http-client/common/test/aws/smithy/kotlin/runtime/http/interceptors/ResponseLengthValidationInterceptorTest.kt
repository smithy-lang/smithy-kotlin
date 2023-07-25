/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.operation.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.io.EOFException
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*

data class ResponseLengthValidationTestInput(val value: String)
data class ResponseLengthValidationTestOutput(val body: HttpBody)

inline fun <reified I> newResponseLengthValidationTestOperation(serialized: HttpRequestBuilder): SdkHttpOperation<I, ResponseLengthValidationTestOutput> =
    SdkHttpOperation.build {
        serializer = HttpSerialize { _, _ -> serialized }

        deserializer = HttpDeserialize { _, response -> ResponseLengthValidationTestOutput(response.body) }

        context {
            // required operation context
            operationName = "TestOperation"
            serviceName = "TestService"
        }
    }

@OptIn(ExperimentalCoroutinesApi::class)
class ResponseLengthValidationInterceptorTest {
    val contentLengthHeaderName = "Content-Length"
    val response = "a".repeat(500).toByteArray()

    private fun getMockClientWithSourceBody(response: ByteArray, responseHeaders: Headers = Headers.Empty): SdkHttpClient {
        val mockEngine = TestEngine { _, request ->
            val body = object : HttpBody.SourceContent() {
                override val contentLength: Long = response.size.toLong()
                override fun readFrom(): SdkSource = response.source()
                override val isOneShot: Boolean get() = false
            }

            val resp = HttpResponse(HttpStatusCode.OK, responseHeaders, body)

            HttpCall(request, resp, Instant.now(), Instant.now())
        }
        return SdkHttpClient(mockEngine)
    }

    private fun getMockClientWithChannelBody(response: ByteArray, responseHeaders: Headers = Headers.Empty): SdkHttpClient {
        val mockEngine = TestEngine { _, request ->
            val body = object : HttpBody.ChannelContent() {
                override val contentLength: Long = response.size.toLong()
                override fun readFrom() = SdkByteReadChannel(response)
                override val isOneShot: Boolean get() = false
            }

            val resp = HttpResponse(HttpStatusCode.OK, responseHeaders, body)

            HttpCall(request, resp, Instant.now(), Instant.now())
        }
        return SdkHttpClient(mockEngine)
    }

    @Test
    fun testCorrectLengthReturned() = runTest {
        val req = HttpRequestBuilder()
        val op = newResponseLengthValidationTestOperation<ResponseLengthValidationTestInput>(req)

        op.interceptors.add(ResponseLengthValidationInterceptor())

        val responseHeaders = Headers {
            append(contentLengthHeaderName, "${response.size}")
        }

        listOf(
            getMockClientWithChannelBody(response, responseHeaders),
            getMockClientWithSourceBody(response, responseHeaders),
        ).forEach { client ->
            val output = op.roundTrip(client, ResponseLengthValidationTestInput("input"))
            output.body.readAll()
        }
    }

    @Test
    fun testNotEnoughBytesReturned() = runTest {
        val req = HttpRequestBuilder()
        val op = newResponseLengthValidationTestOperation<ResponseLengthValidationTestInput>(req)

        op.interceptors.add(ResponseLengthValidationInterceptor())

        val responseHeaders = Headers {
            append(contentLengthHeaderName, "${response.size * 2}") // expect double the actual content length
        }
        listOf(
            getMockClientWithSourceBody(response, responseHeaders),
            getMockClientWithChannelBody(response, responseHeaders),
        ).forEach { client ->
            assertFailsWith<EOFException> {
                val output = op.roundTrip(client, ResponseLengthValidationTestInput("input"))
                output.body.readAll()
            }
        }
    }

    @Test
    fun testTooManyBytesReturned() = runTest {
        val req = HttpRequestBuilder()
        val op = newResponseLengthValidationTestOperation<ResponseLengthValidationTestInput>(req)

        op.interceptors.add(ResponseLengthValidationInterceptor())

        val responseHeaders = Headers {
            append(contentLengthHeaderName, "${response.size / 2}") // expect half the actual content length
        }

        listOf(
            getMockClientWithChannelBody(response, responseHeaders),
            getMockClientWithSourceBody(response, responseHeaders),
        ).forEach { client ->
            assertFailsWith<EOFException> {
                val output = op.roundTrip(client, ResponseLengthValidationTestInput("input"))
                output.body.readAll()
            }
        }
    }

    @Test
    fun testNoContentLengthSkipsValidation() = runTest {
        val req = HttpRequestBuilder()
        val op = newResponseLengthValidationTestOperation<ResponseLengthValidationTestInput>(req)

        op.interceptors.add(ResponseLengthValidationInterceptor())
        val responseHeaders = Headers {}

        listOf(
            getMockClientWithChannelBody(response, responseHeaders),
            getMockClientWithSourceBody(response, responseHeaders),
        ).forEach { client ->
            val output = op.roundTrip(client, ResponseLengthValidationTestInput("input"))
            output.body.readAll()
        }
    }
}
