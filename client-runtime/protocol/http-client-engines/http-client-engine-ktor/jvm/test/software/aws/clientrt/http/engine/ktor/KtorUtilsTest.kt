/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.engine.ktor
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.ktor.client.call.HttpClientCall
import io.ktor.client.statement.HttpResponse
import io.ktor.content.ByteArrayContent as KtorByteArrayContent
import io.ktor.http.*
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import software.aws.clientrt.http.*
import software.aws.clientrt.http.content.ByteArrayContent
import software.aws.clientrt.http.request.HttpRequestBuilder
import software.aws.clientrt.http.request.header
import software.aws.clientrt.http.request.headers
import software.aws.clientrt.http.request.url

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

class KtorUtilsTest {

    @Test
    fun `it converts request builders`() {
        val builder = HttpRequestBuilder()
        builder.method = software.aws.clientrt.http.HttpMethod.POST
        builder.url {
            scheme = Protocol.HTTP
            host = "test.aws.com"
            path = "/kotlin"
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
        actual.url.parameters.getAll("foo")!!.shouldContain("bar")
        actual.url.parameters.getAll("baz")!!.shouldContainAll("qux", "waldo")
        assertEquals("v1", actual.headers["h1"]!!)
        actual.headers.getAll("h2")!!.shouldContainAll("v2", "v3")
    }

    @Test
    fun `it strips Content-Type header`() {
        val builder = HttpRequestBuilder()
        builder.url { host = "test.aws.com" }
        builder.header("Content-Type", "application/json")
        val actual = builder.toKtorRequestBuilder().build()
        actual.headers.contains("Content-Type").shouldBeFalse()
    }

    @Test
    fun `it converts HttpBody variant Bytes`() {
        val builder = HttpRequestBuilder()
        builder.url { host = "test.aws.com" }
        builder.header("Content-Type", "application/json")
        val content = "testing".toByteArray()
        builder.body = ByteArrayContent(content)
        val actual = builder.toKtorRequestBuilder().build()
        actual.headers.contains("Content-Type").shouldBeFalse()
        assertEquals(ContentType.Application.Json, actual.body.contentType)
        val convertedBody = actual.body as KtorByteArrayContent
        assertEquals(content, convertedBody.bytes())
    }

    @Test
    fun `it converts responses`() {
        val builder = HttpRequestBuilder()
        builder.method = software.aws.clientrt.http.HttpMethod.POST
        builder.url {
            host = "test.aws.com"
            path = "/kotlin"
        }
        val request = builder.build()
        val response = MockHttpResponse()
        val actual = response.toSdkHttpResponse(request)
        assertEquals(request, actual.request)
        assertEquals("bar", actual.headers["x-foo"]!!)
        assertEquals(software.aws.clientrt.http.HttpStatusCode.OK, actual.status)
    }

    @Test
    fun `ktor headers are wrapped`() {
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
}
