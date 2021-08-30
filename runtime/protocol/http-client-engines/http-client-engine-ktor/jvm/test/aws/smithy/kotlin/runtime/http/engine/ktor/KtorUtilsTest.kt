/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.http.engine.ktor
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.ktor.client.call.HttpClientCall
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.util.*
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

@InternalAPI
class MockHttpResponse : HttpResponse() {
    override val call: HttpClientCall
        get() = TODO("Not yet implemented")
    override val content: ByteReadChannel = ByteReadChannel.Empty
    override val coroutineContext: CoroutineContext
        get() = TODO("Not yet implemented")
    override val headers: Headers = Headers.build { append("x-foo", "bar") }
    override val requestTime: GMTDate
        get() = TODO("Not yet implemented")
    override val responseTime: GMTDate
        get() = TODO("Not yet implemented")
    override val status: HttpStatusCode = HttpStatusCode.OK
    override val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
}

@InternalAPI
class KtorUtilsTest {

    @Test
    fun itConvertsRequestBuilders() {
        val builder = HttpRequestBuilder()
        builder.method = aws.smithy.kotlin.runtime.http.HttpMethod.POST
        builder.url {
            scheme = Protocol.HTTP
            host = "test.aws.com"
            path = "/kotlin/Tue, 29 Apr 2014 18:30:38 GMT"
            parameters {
                append("foo", "bar")
                appendAll("baz", listOf("qux", "waldo"))
            }
        }
        builder.headers {
            append("h1", "v1")
            appendAll("h2", listOf("v2", "v3"))
        }

        val actual = builder.toKtorRequestBuilder().build()

        assertEquals(HttpMethod.Post, actual.method)
        assertEquals(URLProtocol("http", 80), actual.url.protocol)
        assertEquals("test.aws.com", actual.url.host)
        assertEquals("/kotlin/Tue,%2029%20Apr%202014%2018:30:38%20GMT", actual.url.encodedPath)
        actual.url.parameters.getAll("foo")!!.shouldContain("bar")
        actual.url.parameters.getAll("baz")!!.shouldContainAll("qux", "waldo")
        assertEquals("v1", actual.headers["h1"]!!)
        actual.headers.getAll("h2")!!.shouldContainAll("v2", "v3")
    }

    @Test
    fun itConvertsResponses() {
        val response = MockHttpResponse()
        val actual = response.toSdkHttpResponse()
        assertEquals("bar", actual.headers["x-foo"]!!)
        assertEquals(aws.smithy.kotlin.runtime.http.HttpStatusCode.OK, actual.status)
    }

    @Test
    fun ktorHeadersAreWrapped() {
        val respHeaders = Headers.build {
            append("foo", "bar")
            append("baz", "quux")
            append("baz", "quz")
        }

        val wrapped = KtorHeaders(respHeaders)
        assertEquals(true, wrapped.caseInsensitiveName)
        assertEquals("bar", wrapped["foo"])
        assertEquals(listOf("bar"), wrapped.getAll("foo"))
        assertFalse(wrapped.isEmpty())
        assertEquals(setOf("foo", "baz"), wrapped.names())
        assertEquals(true, wrapped.contains("baz", "quz"))
        assertEquals(true, wrapped.contains("baz"))
    }

    @Test
    fun ktorHeadersAreCopied() {
        val respHeaders = Headers.build {
            append("foo", "bar")
            append("baz", "quux")
            append("baz", "quz")
        }

        val wrapped = KtorHeaders(respHeaders)
        val converted = aws.smithy.kotlin.runtime.http.Headers { appendAll(wrapped) }
        assertEquals(true, converted.caseInsensitiveName)
        assertEquals("bar", converted["foo"])
        assertEquals(listOf("bar"), converted.getAll("foo"))
        assertFalse(converted.isEmpty())
        assertEquals(setOf("foo", "baz"), converted.names())
        assertEquals(true, converted.contains("baz", "quz"))
        assertEquals(true, converted.contains("baz"))
    }
}
