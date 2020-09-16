/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.engine.ktor
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainAll
import io.ktor.client.call.HttpClientCall
import io.ktor.client.statement.HttpResponse
import io.ktor.http.*
import io.ktor.http.Headers
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.*
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import software.aws.clientrt.http.*
import software.aws.clientrt.http.request.HttpRequestBuilder
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

    @Test
    fun `ktor headers are copied`() {
        val respHeaders = Headers.build {
            append("foo", "bar")
            append("baz", "quux")
            append("baz", "quz")
        }

        val wrapped = KtorHeaders(respHeaders)
        val converted = software.aws.clientrt.http.Headers { appendAll(wrapped) }
        assertEquals(true, converted.caseInsensitiveName)
        assertEquals("bar", converted["foo"])
        assertEquals(listOf("bar"), converted.getAll("foo"))
        assertFalse(converted.isEmpty())
        assertEquals(setOf("foo", "baz"), converted.names())
        assertEquals(true, converted.contains("baz", "quz"))
        assertEquals(true, converted.contains("baz"))
    }

    @Test
    fun `KtorContentStream notifies on readAll`() = runBlocking {
        val channel = ByteChannel(true)
        var called = false
        val notify = {
            called = true
        }

        val bytes = "testing".toByteArray()
        channel.writeFully(bytes)
        channel.close()

        val content = KtorContentStream(channel, notify)
        val actual = content.readAll()
        assertEquals(bytes.size, actual.size)
        called.shouldBeTrue()
    }

    @Test
    fun `KtorContentStream notifies on readAvailable`() = runBlocking {
        val channel = ByteChannel(true)
        var called = false
        val notify = {
            called = true
        }

        val bytes = "testing".toByteArray()
        channel.writeFully(bytes)
        channel.close()

        val content = KtorContentStream(channel, notify)
        val dst = ByteArray(16)
        var read = content.readAvailable(dst, 0, 5)
        assertEquals(5, read)
        called.shouldBeFalse()
        read = content.readAvailable(dst, 5, 2)
        assertEquals(2, read)
        called.shouldBeTrue()
    }

    @Test
    fun `KtorContentStream notifies on readFully`() = runBlocking {
        val channel = ByteChannel(true)
        var called = false
        val notify = {
            called = true
        }

        val bytes = "testing".toByteArray()
        channel.writeFully(bytes)
        channel.close()

        val content = KtorContentStream(channel, notify)
        val dst = ByteArray(16)
        content.readFully(dst, 0, 5)
        called.shouldBeFalse()
        content.readFully(dst, 5, 2)
        called.shouldBeTrue()
    }

    @Test
    fun `KtorContentStream notifies on cancel`() = runBlocking {
        val channel = ByteChannel(true)
        var called = false
        val notify = {
            called = true
        }

        val bytes = "testing".toByteArray()
        channel.writeFully(bytes)

        val content = KtorContentStream(channel, notify)
        val dst = ByteArray(16)
        launch {
            assertFailsWith(RuntimeException::class, "testing") {
                content.readFully(dst, 0, 10)
            }
        }
        delay(200)
        content.cancel(RuntimeException("testing"))

        called.shouldBeTrue()
    }
}
