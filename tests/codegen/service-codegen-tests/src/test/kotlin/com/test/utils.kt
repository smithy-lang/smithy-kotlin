package com.test

import kotlinx.serialization.json.JsonElement
import org.gradle.testkit.runner.GradleRunner
import java.io.IOException
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue
import kotlin.test.fail

internal fun startService(
    engineFactory: String = "netty",
    port: Int = 8080,
    closeGracePeriodMillis: Long = 1000,
    closeTimeoutMillis: Long = 1000,
    requestBodyLimit: Long = 10L * 1024 * 1024,
    projectDir: Path,
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

    val gradleCmd = if (isWindows()) "gradlew.bat" else "./gradlew"
    val baseCmd = if (isWindows()) listOf("cmd", "/c", gradleCmd) else listOf(gradleCmd)

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

internal fun cleanupService(proc: Process, gracefulWindow: Long = 5_000L) {
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

internal fun waitForPort(port: Int, timeoutSec: Long = 180, proc: Process? = null): Boolean {
    val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toNanos(timeoutSec)
    while (System.currentTimeMillis() < deadline) {
//        proc?.inputStream?.bufferedReader()?.forEachLine {println(it)}
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
    headers: Map<String, String> = emptyMap(),
): HttpResponse<*> {
    val client = HttpClient.newHttpClient()

    val bodyPublisher = when (data) {
        null -> HttpRequest.BodyPublishers.noBody()
        is ByteArray -> HttpRequest.BodyPublishers.ofByteArray(data)
        is String -> HttpRequest.BodyPublishers.ofString(data)
        is JsonElement -> HttpRequest.BodyPublishers.ofString(data.toString())
        else -> throw IllegalArgumentException(
            "Unsupported body type: ${data::class.qualifiedName}",
        )
    }

    val builder = HttpRequest.newBuilder()
        .uri(URI.create(url))

    contentType?.let { builder.header("Content-Type", it) }
    acceptType ?.let { builder.header("Accept", it) }
    bearerToken?.let { builder.header("Authorization", "Bearer $it") }
    headers.forEach { (name, value) -> builder.header(name, value) }

    val request = builder
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
