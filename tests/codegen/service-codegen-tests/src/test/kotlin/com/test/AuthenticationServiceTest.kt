/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAlgorithm
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
class AuthenticationServiceTest {
    val closeGracePeriodMillis = TestParams.CLOSE_GRACE_PERIOD_MILLIS
    val closeTimeoutMillis = TestParams.CLOSE_TIMEOUT_MILLIS
    val gracefulWindow = TestParams.GRACEFUL_WINDOW
    val requestBodyLimit = TestParams.REQUEST_BODY_LIMIT
    val portListenerTimeout = TestParams.PORT_LISTENER_TIMEOUT

    val port: Int = ServerSocket(0).use { it.localPort }
    val baseUrl = "http://localhost:$port"

    val projectDir: Path = Paths.get("build/service-authentication-test")

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
    fun `checks bearer authentication with correct token`() {
        val response = sendRequest(
            "$baseUrl/only-bearer",
            "POST",
            null,
            "application/json",
            "application/json",
            "correctToken",
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks bearer authentication with wrong token`() {
        val response = sendRequest(
            "$baseUrl/only-bearer",
            "POST",
            null,
            "application/json",
            "application/json",
            "wrongToken",
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks bearer authentication without token`() {
        val response = sendRequest(
            "$baseUrl/only-bearer",
            "POST",
            null,
            "application/json",
            "application/json",
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Missing bearer token", body.message)
    }

    @Test
    fun `checks sigv4 authentication with correct signature`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val response = sendRequest(
            "$baseUrl/only-sigv4",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks sigv4 authentication with wrong region`() {
        val region = "us-east-2"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val response = sendRequest(
            "$baseUrl/only-sigv4",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks sigv4 authentication with wrong service name`() {
        val region = "us-east-1"
        val service = "service-2"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val response = sendRequest(
            "$baseUrl/only-sigv4",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks sigv4 authentication with wrong access key`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIAWRONGEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val response = sendRequest(
            "$baseUrl/only-sigv4",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks sigv4 authentication with wrong secret key`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "WRONGEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val response = sendRequest(
            "$baseUrl/only-sigv4",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks sigv4a authentication with correct signature`() {
        val region = "*"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/sigv4a",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks sigv4a authentication with specific region`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/sigv4a",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks sigv4a authentication with multi regions`() {
        val region = "us-east-*"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/sigv4a",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks sigv4a authentication with wrong region`() {
        val region = "us-east-2"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/sigv4a",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks sigv4a authentication with wrong access key`() {
        val region = "*"
        val service = "service-1"

        val accessKey = "AKIAWRONGEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/sigv4a",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks sigv4a authentication with wrong secret key`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "WRONGEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/sigv4a",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(401, response.statusCode(), "Expected 401")

        val body = Json.decodeFromString(
            ErrorResponse.serializer(),
            response.body(),
        )
        assertEquals(401, body.code)
        assertEquals("Invalid or expired authentication", body.message)
    }

    @Test
    fun `checks multi authentications with bearer token`() {
        val response = sendRequest(
            "$baseUrl/all-authentication",
            "POST",
            null,
            "application/json",
            "application/json",
            "correctToken",
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks multi authentications with sigv4 token`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val response = sendRequest(
            "$baseUrl/all-authentication",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks multi authentications with sigv4a token`() {
        val region = "*"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val response = sendRequest(
            "$baseUrl/all-authentication",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks no authentication`() {
        val response = sendRequest(
            "$baseUrl/no-authentication",
            "POST",
            null,
            "application/json",
            "application/json",
            null,
            mapOf(),
            null,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks sigv4 authentication with body with correct signature`() {
        val region = "us-east-1"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4,
        )

        val requestJson = Json.encodeToJsonElement(
            AuthTestRequest.serializer(),
            AuthTestRequest("this is a test input"),
        )

        val response = sendRequest(
            "$baseUrl/sigv4-authentication-body",
            "POST",
            requestJson,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }

    @Test
    fun `checks sigv4a authentication with body with correct signature`() {
        val region = "*"
        val service = "service-1"

        val accessKey = "AKIACORRECTEXAMPLEACCESSKEY"
        val secretKey = "CORRECTEXAMPLESECRETKEY"

        val creds = Credentials(accessKey, secretKey)

        val signingOptions = AwsSigningOptions(
            credentials = creds,
            service = service,
            region = region,
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC,
        )

        val requestJson = Json.encodeToJsonElement(
            AuthTestRequest.serializer(),
            AuthTestRequest("this is a test input"),
        )

        val response = sendRequest(
            "$baseUrl/sigv4a-authentication-body",
            "POST",
            requestJson,
            "application/json",
            "application/json",
            null,
            mapOf(),
            signingOptions,
        )
        assertIs<HttpResponse<String>>(response)
        assertEquals(201, response.statusCode(), "Expected 201")
    }
}
