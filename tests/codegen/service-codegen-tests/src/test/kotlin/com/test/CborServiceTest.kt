/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import kotlinx.serialization.cbor.Cbor
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.net.ServerSocket
import java.net.http.HttpResponse
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CborServiceTest {
    val closeGracePeriodMillis: Long = 5_000L
    val closeTimeoutMillis: Long = 1_000L
    val requestBodyLimit: Long = 10L * 1024 * 1024
    val port: Int = ServerSocket(0).use { it.localPort }

    val portListnerTimeout = 60L

    val baseUrl = "http://localhost:$port"

    val projectDir: Path = Paths.get("build/service-cbor-test")

    private lateinit var proc: Process

    @BeforeAll
    fun boot() {
        proc = startService("netty", port, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(port, portListnerTimeout)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc)

    @Test
    fun `checks correct POST request`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val input2 = 617
        val requestBytes = cbor.encodeToByteArray(
            PostTestRequest.serializer(),
            PostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")

        val body = cbor.decodeFromByteArray(
            PostTestResponse.serializer(),
            response.body(),
        )

        assertEquals("Hello world!", body.output1)
        assertEquals(input2 + 1, body.output2)
    }

    @Test
    fun `checks unhandled runtime exception in handler`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            ErrorTestRequest.serializer(),
            ErrorTestRequest(input1),
        )

        val response = sendRequest(
            "$baseUrl/error",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(500, response.statusCode(), "Expected 500")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(500, body.code)
        assertEquals("Unexpected error", body.message)
    }

    @Test
    fun `checks wrong content type`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val input2 = 617
        val requestBytes = cbor.encodeToByteArray(
            PostTestRequest.serializer(),
            PostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            "application/json",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(415, response.statusCode(), "Expected 415")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(415, body.code)
        assertEquals("Not acceptable Content‑Type found: 'application/json'. Accepted content types: application/cbor", body.message)
    }

    @Test
    fun `checks missing content type`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val input2 = 617
        val requestBytes = cbor.encodeToByteArray(
            PostTestRequest.serializer(),
            PostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            acceptType = "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(415, response.statusCode(), "Expected 415")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(415, body.code)
        assertEquals("Not acceptable Content‑Type found: '*/*'. Accepted content types: application/cbor", body.message)
    }

    @Test
    fun `checks wrong accept type`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val input2 = 617
        val requestBytes = cbor.encodeToByteArray(
            PostTestRequest.serializer(),
            PostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            "application/cbor",
            "application/json",
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(406, response.statusCode(), "Expected 406")

        assertEquals("""{"code":406,"message":"Not acceptable Accept type found: '[application/json]'. Accepted types: application/cbor"}""", response.body())
    }

    @Test
    fun `checks missing accept type`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val input2 = 617
        val requestBytes = cbor.encodeToByteArray(
            PostTestRequest.serializer(),
            PostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks authentication with correct bearer token`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            AuthTestRequest.serializer(),
            AuthTestRequest(input1),
        )

        val response = sendRequest(
            "$baseUrl/auth",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks authentication with wrong bearer token`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            AuthTestRequest.serializer(),
            AuthTestRequest(input1),
        )

        val response = sendRequest(
            "$baseUrl/auth",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "wrongToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired bearer token", body.message)
    }

    @Test
    fun `checks authentication without bearer token`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            AuthTestRequest.serializer(),
            AuthTestRequest(input1),
        )

        val response = sendRequest(
            "$baseUrl/auth",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Missing bearer token", body.message)
    }

    @Test
    fun `checks malformed input`() {
        val cbor = Cbor { }
        val input1 = 123
        val input2 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            MalformedPostTestRequest.serializer(),
            MalformedPostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("Unexpected EOF: expected 109 more bytes; consumed: 14", body.message)
    }

    @Test
    fun `checks route not found`() {
        val cbor = Cbor { }
        val requestBytes = ByteArray(0)
        val response = sendRequest(
            "$baseUrl/does-not-exist",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(404, response.statusCode(), "Expected 404")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(404, body.code)
        assertEquals("Resource not found", body.message)
    }

    @Test
    fun `checks method not allowed`() {
        val cbor = Cbor { }
        val input1 = 123
        val input2 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            MalformedPostTestRequest.serializer(),
            MalformedPostTestRequest(input1, input2),
        )

        val response = sendRequest(
            "$baseUrl/post",
            "PUT",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(405, response.statusCode(), "Expected 405")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(405, body.code)
        assertEquals("Method not allowed for this resource", body.message)
    }

    @Test
    fun `checks request body limit`() {
        val cbor = Cbor { }
        val overLimitPayload = "x".repeat(requestBodyLimit.toInt() + 1)
        val input2 = 617
        val requestBytes = cbor.encodeToByteArray(
            PostTestRequest.serializer(),
            PostTestRequest(overLimitPayload, input2),
        )
        require(requestBytes.size > 10 * 1024 * 1024)

        val response = sendRequest(
            "$baseUrl/post",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(413, response.statusCode(), "Expected 413")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(413, body.code)
        assertEquals("Request is larger than the limit of 10485760 bytes", body.message)
    }

    @Test
    fun `checks http error`() {
        val cbor = Cbor { }

        val response = sendRequest(
            "$baseUrl/http-error",
            "POST",
            null,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)

        assertEquals(456, response.statusCode(), "Expected 456")
        val body = cbor.decodeFromByteArray(
            HttpError.serializer(),
            response.body(),
        )

        assertEquals(444, body.num)
        assertEquals("this is an error message", body.msg)
    }
}
