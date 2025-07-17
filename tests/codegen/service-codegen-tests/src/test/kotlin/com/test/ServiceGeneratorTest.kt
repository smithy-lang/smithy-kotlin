/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.test

import kotlinx.serialization.ExperimentalSerializationApi
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
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.fail

@Serializable
data class MalformedPostTestRequest(
    val input1: Int,
    val input2: String,
)

@Serializable
data class PostTestRequest(
    val input1: String,
    val input2: Int,
)

@Serializable
data class PostTestResponse(
    val output1: String? = null,
    val output2: Int? = null,
)

@Serializable
data class AuthTestRequest(
    val input1: String,
)

@Serializable
data class PutTestRequest(
    val input1: String,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceGeneratorTest {
    val serviceName = "ServiceGeneratorTest"
    val packageName = "com.test"
    val closeGracePeriodMillis: Long = 5_000L
    val closeTimeoutMillis: Long = 1_000L
    val requestBodyLimit: Long = 10L * 1024 * 1024
    val port: Int = ServerSocket(0).use { it.localPort }

    val packagePath = packageName.replace('.', '/')
    val baseUrl = "http://localhost:$port"

    val projectDir: Path = Paths.get("build/generated-service")

    private lateinit var proc: Process

    @BeforeAll
    fun boot() {
        proc = startService("netty", port, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)

        val ready = waitForPort(port, 180)
        assertTrue(ready, "Service did not start within 180 s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc)

    @Test
    fun `generates service and all necessary files`() {
        assertTrue(projectDir.resolve("build.gradle.kts").exists())
        assertTrue(projectDir.resolve("settings.gradle.kts").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/Main.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/Routing.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/config/ServiceFrameworkConfig.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/framework/ServiceFramework.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/plugins/ContentTypeGuard.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/plugins/ErrorHandler.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/utils/Logging.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/auth/Authentication.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/auth/Validation.kt").exists())

        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/model/PostTestRequest.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/model/PostTestResponse.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/serde/PostTestOperationSerializer.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/serde/PostTestOperationDeserializer.kt").exists())
        assertTrue(projectDir.resolve("src/main/kotlin/$packagePath/operations/PostTestOperation.kt").exists())
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks service with netty engine`() {
        val nettyPort: Int = ServerSocket(0).use { it.localPort }
        val nettyProc = startService("netty", nettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(nettyPort, 180)
        assertTrue(ready, "Service did not start within 180 s")
        cleanupService(nettyProc)
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks service with cio engine`() {
        val cioPort: Int = ServerSocket(0).use { it.localPort }
        val cioProc = startService("cio", cioPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(cioPort, 180)
        assertTrue(ready, "Service did not start within 180 s")
        cleanupService(cioProc)
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks service with jetty jakarta engine`() {
        val jettyPort: Int = ServerSocket(0).use { it.localPort }
        val jettyProc = startService("jetty-jakarta", jettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForPort(jettyPort, 180)
        assertTrue(ready, "Service did not start within 180 s")
        cleanupService(jettyProc)
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(201, response.statusCode(), "Expected 201 OK")

        val body = cbor.decodeFromByteArray(
            PostTestResponse.serializer(),
            response.body(),
        )

        assertEquals("Hello world!", body.output1)
        assertEquals(input2 + 1, body.output2)
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks unhandled runtime exception in handler`() {
        val cbor = Cbor { }
        val input1 = "Hello"
        val requestBytes = cbor.encodeToByteArray(
            PutTestRequest.serializer(),
            PutTestRequest(input1),
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
        assertEquals(500, response.statusCode(), "Expected 500 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(415, response.statusCode(), "Expected 415 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(415, response.statusCode(), "Expected 415 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(406, response.statusCode(), "Expected 406 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(201, response.statusCode(), "Expected 201 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(201, response.statusCode(), "Expected 201 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(401, response.statusCode(), "Expected 401 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(401, response.statusCode(), "Expected 401 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(400, response.statusCode(), "Expected 400 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks route not found`() {
        val requestBytes = ByteArray(0)
        val response = sendRequest(
            "$baseUrl/does-not-exist",
            "POST",
            requestBytes,
            "application/cbor",
            "application/cbor",
        )
        assertIs<HttpResponse<ByteArray>>(response)
        assertEquals(404, response.statusCode(), "Expected 404 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
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
        assertEquals(405, response.statusCode(), "Expected 405 OK")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks request body limit`() {
        val cbor = Cbor { }
        val overLimitPayload = "x".repeat(10 * 1024 * 1024 + 1)
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
        assertEquals(413, response.statusCode(), "Expected 413 Payload Too Large")
    }
}

@OptIn(ExperimentalPathApi::class)
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

@OptIn(ExperimentalPathApi::class)
internal fun ServiceGeneratorTest.cleanupService(proc: Process) {
    val gracefulWindow = closeGracePeriodMillis + closeTimeoutMillis
    val okExitCodes = if (isWindows()) {
        setOf(0, 1, 143, -1, -1073741510)
    } else {
        setOf(0, 143)
    }

    try {
        killProcess(proc)
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

private fun killProcess(proc: Process) {
    if (isWindows()) {
        Runtime.getRuntime().exec("taskkill /F /T /PID ${proc.pid()}")
    } else {
        proc.destroy()
    }
}

internal fun waitForPort(port: Int, timeoutSec: Long = 180): Boolean {
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
