/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
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
class JsonServiceTest {
    val closeGracePeriodMillis: Long = 5_000L
    val closeTimeoutMillis: Long = 1_000L
    val requestBodyLimit: Long = 10L * 1024 * 1024
    val port: Int = ServerSocket(0).use { it.localPort }

    val portListnerTimeout = 100L

    val baseUrl = "http://localhost:$port"

    val projectDir: Path = Paths.get("build/service-json-test")

    private lateinit var proc: Process

    @BeforeAll
    fun boot() {
        proc = startService("netty", port, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit, projectDir)
        val ready = waitForPort(port, portListnerTimeout, proc)
        assertTrue(ready, "Service did not start within $portListnerTimeout s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc)

    @Test
    fun `checks http-header`() {
        val response = sendRequest(
            "$baseUrl/http-header",
            "POST",
            null,
            "application/json",
            "application/json",
            "correctToken",
            mapOf("X-Request-Header" to "header", "X-Request-Headers-hhh" to "headers"),
        )
        assertIs<HttpResponse<String>>(response)

        assertEquals(201, response.statusCode(), "Expected 201")

        assertEquals("headers", response.headers().firstValue("X-Response-Header").get())
        assertEquals("header", response.headers().firstValue("X-Response-Headers-hhh").get())
    }

    @Test
    fun `checks http-label`() {
        val json = Json { }
        val response = sendRequest(
            "$baseUrl/http-label/labelValue",
            "GET",
            null,
            "application/json",
            "application/json",
            "correctToken",
        )
        assertIs<HttpResponse<String>>(response)

        assertEquals(200, response.statusCode(), "Expected 200")
        val body = json.decodeFromString(
            HttpLabelTestOutputResponse.serializer(),
            response.body(),
        )
        assertEquals("labelValue", body.output)
    }

    @Test
    fun `checks http-query`() {
        val json = Json { }
        val response = sendRequest(
            "$baseUrl/http-query?query=123&qqq=kotlin",
            "DELETE",
            null,
            "application/json",
            "application/json",
            "correctToken",
        )
        assertIs<HttpResponse<String>>(response)

        assertEquals(200, response.statusCode(), "Expected 200")
        val body = json.decodeFromString(
            HttpQueryTestOutputResponse.serializer(),
            response.body(),
        )
        assertEquals("123kotlin", body.output)
    }

    @Test
    fun `checks http-payload string`() {
        val response = sendRequest(
            "$baseUrl/http-payload/string",
            "POST",
            "This is the entire content",
            "text/plain",
            "text/plain",
            "correctToken",
        )
        assertIs<HttpResponse<String>>(response)

        assertEquals(201, response.statusCode(), "Expected 201")
        assertEquals("This is the entire content", response.body())
    }

    @Test
    fun `checks http-payload structure`() {
        val json = Json { }

        val requestJson = json.encodeToJsonElement(
            HttpStructurePayloadTestStructure.serializer(),
            HttpStructurePayloadTestStructure(
                "content",
                123,
                456.toFloat(),
            ),
        )

        val response = sendRequest(
            "$baseUrl/http-payload/structure",
            "POST",
            requestJson,
            "application/json",
            "application/json",
            "correctToken",
        )
        assertIs<HttpResponse<String>>(response)
        println(response.body())
        assertEquals(201, response.statusCode(), "Expected 201")
        val body = json.decodeFromString(
            HttpStructurePayloadTestStructure.serializer(),
            response.body(),
        )
        assertEquals("content", body.content1)
        assertEquals(123, body.content2)
        assertEquals(456.toFloat(), body.content3)
    }


}
