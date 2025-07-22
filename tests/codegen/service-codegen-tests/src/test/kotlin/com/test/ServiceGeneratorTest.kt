/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@Serializable
data class ErrorResponse(val code: Int, val message: String)

@Serializable
data class MalformedPostTestRequest(val input1: Int, val input2: String)

@Serializable
data class PostTestRequest(val input1: String, val input2: Int)

@Serializable
data class PostTestResponse(val output1: String? = null, val output2: Int? = null)

@Serializable
data class AuthTestRequest(val input1: String)

@Serializable
data class ErrorTestRequest(val input1: String)

@Serializable
data class RequiredConstraintTestRequest(val requiredInput: String? = null, val notRequiredInput: String? = null)

@Serializable
data class LengthConstraintTestRequest(
    val greaterLengthInput: String,
    val smallerLengthInput: List<String>,
    val betweenLengthInput: Map<String, String>,
)

@Serializable
data class PatternConstraintTestRequest(val patternInput1: String, val patternInput2: String)

@Serializable
data class RangeConstraintTestRequest(val betweenInput: Int, val greaterInput: Double, val smallerInput: Float)

@Serializable
data class UniqueItemsConstraintTestRequest(val notUniqueItemsListInput: List<String>, val uniqueItemsListInput: List<String>)

@Serializable
data class NestedUniqueItemsConstraintTestRequest(val nestedUniqueItemsListInput: List<List<String>>)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceGeneratorTest {
    val packageName = "com.test"
    val closeGracePeriodMillis: Long = 5_000L
    val closeTimeoutMillis: Long = 1_000L
    val requestBodyLimit: Long = 10L * 1024 * 1024
    val port: Int = ServerSocket(0).use { it.localPort }

    val portListnerTimeout = 10L

    val packagePath = packageName.replace('.', '/')
    val baseUrl = "http://localhost:$port"

    val projectDir: Path = Paths.get("build/generated-service")

    private lateinit var proc: Process

    @BeforeAll
    fun boot() {
        proc = startService("netty", port, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(port, portListnerTimeout, proc)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc)

    @Test
    fun `checks service with netty engine`() {
        val nettyPort: Int = ServerSocket(0).use { it.localPort }
        val nettyProc = startService("netty", nettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(nettyPort, portListnerTimeout, nettyProc)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
        cleanupService(nettyProc)
    }

    @Test
    fun `checks service with cio engine`() {
        val cioPort: Int = ServerSocket(0).use { it.localPort }
        val cioProc = startService("cio", cioPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(cioPort, portListnerTimeout, cioProc)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
        cleanupService(cioProc)
    }

    @Test
    fun `checks service with jetty jakarta engine`() {
        val jettyPort: Int = ServerSocket(0).use { it.localPort }
        val jettyProc = startService("jetty-jakarta", jettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(jettyPort, portListnerTimeout, jettyProc)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
        cleanupService(jettyProc)
    }

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
        assertEquals("Malformed CBOR input", body.message)
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

    /* Tests for checking constraint traits work */
    @Test
    fun `checks required constraint providing all data`() {
        val cbor = Cbor { }
        val requiredInput = "Hello"
        val notRequiredInput = "World"
        val requestBytes = cbor.encodeToByteArray(
            RequiredConstraintTestRequest.serializer(),
            RequiredConstraintTestRequest(requiredInput, notRequiredInput),
        )

        val response = sendRequest(
            "$baseUrl/required-constraint",
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
    fun `checks required constraint without providing non-required data`() {
        val cbor = Cbor { }
        val requiredInput = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            RequiredConstraintTestRequest.serializer(),
            RequiredConstraintTestRequest(requiredInput, null),
        )

        val response = sendRequest(
            "$baseUrl/required-constraint",
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
    fun `checks required constraint without providing required data`() {
        val cbor = Cbor { }
        val nonRequiredInput = "World"
        val requestBytes = cbor.encodeToByteArray(
            RequiredConstraintTestRequest.serializer(),
            RequiredConstraintTestRequest(null, nonRequiredInput),
        )

        val response = sendRequest(
            "$baseUrl/required-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("requiredInput must be provided", body.message)
    }

    @Test
    fun `checks length constraint providing correct data`() {
        val cbor = Cbor { }
        val greaterLengthInput = "1234567890"
        val smallerLengthInput = listOf("1", "2", "3")
        val betweenLengthInput = mapOf("1" to "2", "3" to "4")

        val requestBytes = cbor.encodeToByteArray(
            LengthConstraintTestRequest.serializer(),
            LengthConstraintTestRequest(greaterLengthInput, smallerLengthInput, betweenLengthInput),
        )

        val response = sendRequest(
            "$baseUrl/length-constraint",
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
    fun `checks length constraint violating greater than or equal to`() {
        val cbor = Cbor { }
        val greaterLengthInput = "1"
        val smallerLengthInput = listOf("1", "2", "3")
        val betweenLengthInput = mapOf("1" to "2", "3" to "4")

        val requestBytes = cbor.encodeToByteArray(
            LengthConstraintTestRequest.serializer(),
            LengthConstraintTestRequest(greaterLengthInput, smallerLengthInput, betweenLengthInput),
        )

        val response = sendRequest(
            "$baseUrl/length-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("greaterLengthInput's size must be greater than or equal to 3", body.message)
    }

    @Test
    fun `checks length constraint violating smaller than or equal to`() {
        val cbor = Cbor { }
        val greaterLengthInput = "123456789"
        val smallerLengthInput = listOf("1", "2", "3", "4", "5", "6")
        val betweenLengthInput = mapOf("1" to "2", "3" to "4")

        val requestBytes = cbor.encodeToByteArray(
            LengthConstraintTestRequest.serializer(),
            LengthConstraintTestRequest(greaterLengthInput, smallerLengthInput, betweenLengthInput),
        )

        val response = sendRequest(
            "$baseUrl/length-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("smallerLengthInput's size must be less than or equal to 3", body.message)
    }

    @Test
    fun `checks length constraint violating between`() {
        val cbor = Cbor { }
        val greaterLengthInput = "123456789"
        val smallerLengthInput = listOf("1", "2")
        val betweenLengthInput = mapOf("1" to "2", "3" to "4", "5" to "6", "7" to "8")

        val requestBytes = cbor.encodeToByteArray(
            LengthConstraintTestRequest.serializer(),
            LengthConstraintTestRequest(greaterLengthInput, smallerLengthInput, betweenLengthInput),
        )

        val response = sendRequest(
            "$baseUrl/length-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("betweenLengthInput's size must be between 1 and 2", body.message)
    }

    @Test
    fun `checks pattern constraint providing correct data`() {
        val cbor = Cbor { }
        val patternInput1 = "qwertyuiop"
        val patternInput2 = "qwe123rty"

        val requestBytes = cbor.encodeToByteArray(
            PatternConstraintTestRequest.serializer(),
            PatternConstraintTestRequest(patternInput1, patternInput2),
        )

        val response = sendRequest(
            "$baseUrl/pattern-constraint",
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
    fun `checks pattern constraint providing incorrect pattern 1`() {
        val cbor = Cbor { }
        val patternInput1 = "qwertyuiop1"
        val patternInput2 = "qwe123rty"

        val requestBytes = cbor.encodeToByteArray(
            PatternConstraintTestRequest.serializer(),
            PatternConstraintTestRequest(patternInput1, patternInput2),
        )

        val response = sendRequest(
            "$baseUrl/pattern-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("Value `qwertyuiop1` does not match required pattern: `^[A-Za-z]+\$`", body.message)
    }

    @Test
    fun `checks pattern constraint providing incorrect pattern 2`() {
        val cbor = Cbor { }
        val patternInput1 = "qwertyuiop"
        val patternInput2 = "qwerty"

        val requestBytes = cbor.encodeToByteArray(
            PatternConstraintTestRequest.serializer(),
            PatternConstraintTestRequest(patternInput1, patternInput2),
        )

        val response = sendRequest(
            "$baseUrl/pattern-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("Value `qwerty` does not match required pattern: `[1-9]+`", body.message)
    }

    @Test
    fun `checks range constraint providing correct data`() {
        val cbor = Cbor { }
        val betweenInput = 3
        val greaterInput = (-1).toDouble()
        val smallerInput = 8.toFloat()

        val requestBytes = cbor.encodeToByteArray(
            RangeConstraintTestRequest.serializer(),
            RangeConstraintTestRequest(betweenInput, greaterInput, smallerInput),
        )

        val response = sendRequest(
            "$baseUrl/range-constraint",
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
    fun `checks range constraint violating greater than or equal to`() {
        val cbor = Cbor { }
        val betweenInput = 3
        val greaterInput = (-100).toDouble()
        val smallerInput = 8.toFloat()

        val requestBytes = cbor.encodeToByteArray(
            RangeConstraintTestRequest.serializer(),
            RangeConstraintTestRequest(betweenInput, greaterInput, smallerInput),
        )

        val response = sendRequest(
            "$baseUrl/range-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("greaterInput must be greater than or equal to -10", body.message)
    }

    @Test
    fun `checks range constraint violating smaller than or equal to`() {
        val cbor = Cbor { }
        val betweenInput = 3
        val greaterInput = (-1).toDouble()
        val smallerInput = 10.toFloat()

        val requestBytes = cbor.encodeToByteArray(
            RangeConstraintTestRequest.serializer(),
            RangeConstraintTestRequest(betweenInput, greaterInput, smallerInput),
        )

        val response = sendRequest(
            "$baseUrl/range-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("smallerInput must be less than or equal to 9", body.message)
    }

    @Test
    fun `checks range constraint violating between`() {
        val cbor = Cbor { }
        val betweenInput = -1
        val greaterInput = (-1).toDouble()
        val smallerInput = 8.toFloat()

        val requestBytes = cbor.encodeToByteArray(
            RangeConstraintTestRequest.serializer(),
            RangeConstraintTestRequest(betweenInput, greaterInput, smallerInput),
        )

        val response = sendRequest(
            "$baseUrl/range-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("betweenInput must be between 0 and 5", body.message)
    }

    @Test
    fun `checks unique items constraint providing correct data`() {
        val cbor = Cbor { }
        val notUniqueInput = listOf("1", "2", "3", "4", "5", "1", "2", "3", "3", "4", "5")
        val uniqueInput = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")

        val requestBytes = cbor.encodeToByteArray(
            UniqueItemsConstraintTestRequest.serializer(),
            UniqueItemsConstraintTestRequest(notUniqueInput, uniqueInput),
        )

        val response = sendRequest(
            "$baseUrl/unique-items-constraint",
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
    fun `checks unique items constraint providing non unique list`() {
        val cbor = Cbor { }
        val notUniqueInput = listOf("1", "2", "3", "4", "5", "1", "2", "3", "3", "4", "5")
        val uniqueInput = listOf("1", "2", "3", "4", "5", "1", "2", "3", "3", "4", "5")

        val requestBytes = cbor.encodeToByteArray(
            UniqueItemsConstraintTestRequest.serializer(),
            UniqueItemsConstraintTestRequest(notUniqueInput, uniqueInput),
        )

        val response = sendRequest(
            "$baseUrl/unique-items-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("uniqueItemsListInput must have unique items", body.message)
    }

    @Test
    fun `checks unique items constraint providing unique nested list`() {
        val cbor = Cbor { }
        val nestedUniqueItemsListInput = listOf(listOf("1"), listOf("2", "3"), listOf("4"), listOf("5", "6", "7"))

        val requestBytes = cbor.encodeToByteArray(
            NestedUniqueItemsConstraintTestRequest.serializer(),
            NestedUniqueItemsConstraintTestRequest(nestedUniqueItemsListInput),
        )

        val response = sendRequest(
            "$baseUrl/nested-unique-items-constraint",
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
    fun `checks unique items constraint providing non-unique nested list`() {
        val cbor = Cbor { }
        val nestedUniqueItemsListInput = listOf(listOf("1"), listOf("2", "2"), listOf("4"), listOf("5", "6", "7"))

        val requestBytes = cbor.encodeToByteArray(
            NestedUniqueItemsConstraintTestRequest.serializer(),
            NestedUniqueItemsConstraintTestRequest(nestedUniqueItemsListInput),
        )

        val response = sendRequest(
            "$baseUrl/nested-unique-items-constraint",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
            "correctToken",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("member must have unique items", body.message)
    }
}

internal fun ServiceGeneratorTest.startService(
    engineFactory: String = "netty",
    port: Int = 8080,
    closeGracePeriodMillis: Long = 1000,
    closeTimeoutMillis: Long = 1000,
    requestBodyLimit: Long = 10L * 1024 * 1024,
): Process {
    if (!Files.exists(projectDir.resolve("gradlew"))) {
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(
                "wrapper",
                "--quiet",
            )
            .build()
    }
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    val gradleCmd = if (isWindows) "gradlew.bat" else "./gradlew"
    val baseCmd = if (isWindows) listOf("cmd", "/c", gradleCmd) else listOf(gradleCmd)

    return ProcessBuilder(
        baseCmd + listOf(
            "--no-daemon",
            "--quiet",
            "run",
            "--args=--engineFactory $engineFactory " +
                "--port $port " +
                "--closeGracePeriodMillis ${closeGracePeriodMillis.toInt()} " +
                "--closeTimeoutMillis ${closeTimeoutMillis.toInt()} " +
                "--requestBodyLimit $requestBodyLimit",
        ),
    )
        .directory(projectDir.toFile())
        .redirectErrorStream(true)
        .start()
}

internal fun ServiceGeneratorTest.cleanupService(proc: Process) {
    val gracefulWindow = closeGracePeriodMillis + closeTimeoutMillis
    val okExitCodes = if (isWindows()) {
        setOf(0, 1, 143, -1, -1073741510)
    } else {
        setOf(0, 143)
    }

    try {
        proc.destroy()
        val exited = proc.waitFor(gracefulWindow, TimeUnit.MILLISECONDS)

        if (!exited) {
            proc.destroyForcibly()
            fail("Service did not shut down within $gracefulWindow ms")
        }

        assertTrue(
            proc.exitValue() in okExitCodes,
            "Service exited with ${proc.exitValue()} – shutdown not graceful?",
        )
    } catch (e: Exception) {
        proc.destroyForcibly()
        throw e
    }
}

private fun isWindows() = System.getProperty("os.name").lowercase().contains("windows")

internal fun waitForPort(port: Int, timeoutSec: Long = 180, proc: Process): Boolean {
    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toNanos(timeoutSec)
    while (System.currentTimeMillis() < deadline) {
        try {
            Socket("localhost", port).use {
                return true // Port is available
            }
        } catch (e: IOException) {
            Thread.sleep(100)
        }
    }
    return false
}

internal fun sendRequest(
    url: String,
    method: String,
    data: Any? = null,
    contentType: String? = null,
    acceptType: String? = null,
    bearerToken: String? = null,
): HttpResponse<*> {
    val client = HttpClient.newHttpClient()

    val bodyPublisher = when (data) {
        null -> HttpRequest.BodyPublishers.noBody()
        is ByteArray -> HttpRequest.BodyPublishers.ofByteArray(data)
        is String -> HttpRequest.BodyPublishers.ofString(data)
        else -> throw IllegalArgumentException(
            "Unsupported body type: ${data::class.qualifiedName}",
        )
    }

    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .apply {
            contentType?.let { header("Content-Type", it) }
            acceptType?.let { header("Accept", it) }
            bearerToken?.let { header("Authorization", "Bearer $it") }
        }
        .method(method, bodyPublisher)
        .build()

    val bodyHandler = when {
        acceptType?.contains("json", ignoreCase = true) == true ||
            acceptType?.startsWith("text", ignoreCase = true) == true
        -> HttpResponse.BodyHandlers.ofString()
        else -> HttpResponse.BodyHandlers.ofByteArray()
    }

    return client.send(request, bodyHandler)
}
