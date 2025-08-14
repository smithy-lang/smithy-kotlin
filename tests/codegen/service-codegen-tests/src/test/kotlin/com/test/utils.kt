package com.test

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSignatureType
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSignedBodyHeader
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAlgorithm
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAttributes
import aws.smithy.kotlin.runtime.auth.awssigning.DefaultAwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.collections.attributesOf
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.auth.AwsHttpSigner
import aws.smithy.kotlin.runtime.http.auth.SignHttpRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.runBlocking
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
import java.util.Locale
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

data class AwsSigningOptions(
    val credentials: Credentials,
    val service: String, // e.g., "execute-api", "s3", "es", "kinesis"
    val region: String?, // e.g., "us-west-2" (null when using SigV4A)
    val algorithm: AwsSigningAlgorithm, // AwsSigningAlgorithm.SIGV4 or SIGV4A
    val signatureType: AwsSignatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS,
    val signedBodyHeader: AwsSignedBodyHeader = AwsSignedBodyHeader.NONE,
    val useDoubleUriEncode: Boolean = true,
    val normalizeUriPath: Boolean = true,
)

@OptIn(InternalApi::class)
internal fun sendRequest(
    url: String,
    method: String,
    data: Any? = null,
    contentType: String? = null,
    acceptType: String? = null,
    bearerToken: String? = null,
    headers: Map<String, String> = emptyMap(),
    awsSigning: AwsSigningOptions? = null,
): HttpResponse<*> {
    require(!(awsSigning != null && bearerToken != null)) {
        "Cannot use bearerToken and awsSigning together."
    }
    val httpMethod = method.uppercase(Locale.ROOT)
    require(!(httpMethod in setOf("GET", "HEAD") && data != null)) {
        "GET/HEAD with a body is not supported."
    }

    val client = HttpClient.newHttpClient()

    val uri = URI.create(url)
    val defaultPort = if (uri.scheme.equals("https", true)) 443 else 80
    val hostHeader = buildString {
        append(uri.host)
        if (uri.port != -1 && uri.port != defaultPort) append(":${uri.port}")
    }
    val baseHeaders = linkedMapOf<String, String>().apply {
        put("Host", hostHeader)
        contentType?.let { put("Content-Type", it) }
        acceptType?.let { put("Accept", it) }
        putAll(headers)
        bearerToken?.let { put("Authorization", "Bearer $it") }
    }

    val bodyPublisher = when (data) {
        null -> HttpRequest.BodyPublishers.noBody()
        is ByteArray -> HttpRequest.BodyPublishers.ofByteArray(data)
        is String -> HttpRequest.BodyPublishers.ofString(data)
        is JsonElement -> HttpRequest.BodyPublishers.ofString(data.toString())
        else -> error("Unsupported body type: ${data::class.qualifiedName}")
    }

    val signedHeaders = if (awsSigning != null) {
        requireNotNull(awsSigning.region) { "awsSigning.region is required." }

        val unsigned = awsSigning.signedBodyHeader == AwsSignedBodyHeader.NONE
        val signer = AwsHttpSigner(
            AwsHttpSigner.Config().apply {
                this.signer = DefaultAwsSigner
                this.service = awsSigning.service
                this.isUnsignedPayload = unsigned
                this.algorithm = awsSigning.algorithm
            },
        )

        val ktorReq = HttpRequestBuilder().apply {
            this.method = HttpMethod.parse(httpMethod)
            url(Url.parse(url))
            headers {
                baseHeaders.forEach { (k, v) -> append(k, v) }
            }
            if (!unsigned) {
                body = when {
                    data is ByteArray -> HttpBody.fromBytes(data)
                    data is String -> HttpBody.fromBytes(data.toByteArray())
                    data == null -> HttpBody.Empty
                    else -> error("Unsupported body type: ${data::class.qualifiedName}")
                }
            }
        }

        val attrs = attributesOf {
            Credentials to awsSigning.credentials
            AwsSigningAttributes.SigningRegion to awsSigning.region
            AwsSigningAttributes.SigningDate to Instant.now()
            if (unsigned) {
                AwsSigningAttributes.HashSpecification to HashSpecification.UnsignedPayload
                AwsSigningAttributes.SignedBodyHeader to AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
            } else {
                AwsSigningAttributes.HashSpecification to HashSpecification.CalculateFromPayload
                AwsSigningAttributes.SignedBodyHeader to AwsSignedBodyHeader.X_AMZ_CONTENT_SHA256
            }
        }

        runBlocking {
            signer.sign(SignHttpRequest(ktorReq, awsSigning.credentials, attrs))
        }

        ktorReq.headers.build().entries().associate { (k, vs) -> k to vs.joinToString(",") }
    } else {
        baseHeaders
    }

    val builder = HttpRequest.newBuilder().uri(uri)
    signedHeaders.forEach { (k, v) ->
        if (!k.equals("Host", ignoreCase = true)) {
            builder.header(k, v)
        }
    }
    val request = builder.method(httpMethod, bodyPublisher).build()

    val bodyHandler =
        if (acceptType?.contains("json", true) == true ||
            acceptType?.startsWith("text", true) == true
        ) {
            HttpResponse.BodyHandlers.ofString()
        } else {
            HttpResponse.BodyHandlers.ofByteArray()
        }

    return client.send(request, bodyHandler)
}
