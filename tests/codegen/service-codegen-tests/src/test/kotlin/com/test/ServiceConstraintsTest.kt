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

/* Tests for checking constraint traits work */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceConstraintsTest {
    val closeGracePeriodMillis = TestParams.CLOSE_GRACE_PERIOD_MILLIS
    val closeTimeoutMillis = TestParams.CLOSE_TIMEOUT_MILLIS
    val gracefulWindow = TestParams.GRACEFUL_WINDOW
    val requestBodyLimit = TestParams.REQUEST_BODY_LIMIT
    val portListenerTimeout = TestParams.PORT_LISTENER_TIMEOUT

    val port: Int = ServerSocket(0).use { it.localPort }
    val baseUrl = "http://localhost:$port"

    val projectDir: Path = Paths.get("build/service-constraints-test")

    private lateinit var proc: Process

    @BeforeAll
    fun boot() {
        proc = startService("netty", port, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(port, portListenerTimeout)
        assertTrue(ready, "Service did not start within $portListenerTimeout s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc, gracefulWindow)

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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("`requiredInput` must be provided", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("The size of `greaterLengthInput` must be greater than or equal to 3", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("The size of `smallerLengthInput` must be less than or equal to 3", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("The size of `betweenLengthInput` must be between 1 and 2 (inclusive)", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("`greaterInput` must be greater than or equal to -10", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("`smallerInput` must be less than or equal to 9", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("`betweenInput` must be between 0 and 5 (inclusive)", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("`uniqueItemsListInput` must contain only unique items, duplicate values are not allowed", body.message)
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
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(400, response.statusCode(), "Expected 400")

        val body = cbor.decodeFromByteArray(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(400, body.code)
        assertEquals("`member` must contain only unique items, duplicate values are not allowed", body.message)
    }

    @Test
    fun `checks unique items constraint providing non-unique nested nested list`() {
        val cbor = Cbor { }
        val doubleNestedUniqueItemsListInput = listOf(
            listOf(listOf("0"), listOf("1", "2"), listOf("6"), listOf("9", "10", "11")),
            listOf(listOf("2"), listOf("7", "2"), listOf("4"), listOf("5", "6", "5")),
            listOf(listOf("1"), listOf("1", "2"), listOf("4"), listOf("5", "6", "7")),
        )

        val requestBytes = cbor.encodeToByteArray(
            DoubleNestedUniqueItemsConstraintTestRequest.serializer(),
            DoubleNestedUniqueItemsConstraintTestRequest(doubleNestedUniqueItemsListInput),
        )

        val response = sendRequest(
            "$baseUrl/double-nested-unique-items-constraint",
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
        assertEquals("`member` must contain only unique items, duplicate values are not allowed", body.message)
    }
}
