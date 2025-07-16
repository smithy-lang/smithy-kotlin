/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.service

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.aws.protocols.RpcV2Cbor
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
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
    val defaultModel = loadModelFromResource("service-generator-test.smithy")
    val serviceName = "ServiceGeneratorTest"
    val packageName = "com.test"
    val closeGracePeriodMillis: Long = 5_000L
    val closeTimeoutMillis: Long = 1_000L
    val requestBodyLimit: Long = 10L * 1024 * 1024
    val port: Int = ServerSocket(0).use { it.localPort }

    val packagePath = packageName.replace('.', '/')
    val baseUrl = "http://localhost:$port"

    lateinit var projectDir: Path

    private lateinit var proc: Process
    private lateinit var manifest: MockManifest

    @BeforeAll
    fun boot(@TempDir tempDir: Path) {
        projectDir = tempDir
        manifest = generateService()

        val postTestOperation = """
            package $packageName.operations

            import $packageName.model.PostTestRequest
            import $packageName.model.PostTestResponse

            public fun handlePostTestRequest(req: PostTestRequest): PostTestResponse {
                val response = PostTestResponse.Builder()
                val input1 = req.input1 ?: ""
                val input2 = req.input2 ?: 0
                response.output1 = input1 + " world!"
                response.output2 = input2 + 1
                return response.build()
            }
        """.trimIndent()
        manifest.writeFile("src/main/kotlin/$packagePath/operations/PostTestOperation.kt", postTestOperation)

        val errorTestOperation = """
            package $packageName.operations

            import $packageName.model.ErrorTestRequest
            import $packageName.model.ErrorTestResponse

            public fun handleErrorTestRequest(req: ErrorTestRequest): ErrorTestResponse {
                val variable: String? = null
                val error = variable!!.length
                return ErrorTestResponse.Builder().build()
            }
        """.trimIndent()
        manifest.writeFile("src/main/kotlin/$packagePath/operations/ErrorTestOperation.kt", errorTestOperation)

        val bearerValidation = """
            package $packageName.auth

            public fun bearerValidation(token: String): UserPrincipal? {
                if (token == "correctToken") return UserPrincipal("Authenticated User") else return null
            }
        """.trimIndent()
        manifest.writeFile("src/main/kotlin/$packagePath/auth/Validation.kt", bearerValidation)
        writeService(manifest)
        proc = startService("netty", port, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)

        val ready = waitForLog(
            proc.inputStream.bufferedReader(),
            text = "Server started",
            timeoutSec = 30,
        )
        assertTrue(ready, "Service did not start within 30 s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc)

    @Test
    fun `generates service and all necessary files`() {
        assertTrue(manifest.hasFile("build.gradle.kts"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/Main.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/Routing.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/config/ServiceFrameworkConfig.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/framework/ServiceFramework.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/plugins/ContentTypeGuard.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/plugins/ErrorHandler.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/utils/Logging.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/auth/Authentication.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/auth/Validation.kt"))

        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/model/PostTestRequest.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/model/PostTestResponse.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/serde/PostTestOperationSerializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/serde/PostTestOperationDeserializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/$packagePath/operations/PostTestOperation.kt"))
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks service with netty engine`() {
        val nettyPort: Int = ServerSocket(0).use { it.localPort }
        val nettyProc = startService("netty", nettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForLog(
            nettyProc.inputStream.bufferedReader(),
            text = "Server started",
            timeoutSec = 30,
        )
        assertTrue(ready, "Service did not start within 30 s")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks service with cio engine`() {
        val cioPort: Int = ServerSocket(0).use { it.localPort }
        val cioProc = startService("cio", cioPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForLog(
            cioProc.inputStream.bufferedReader(),
            text = "Server started",
            timeoutSec = 30,
        )
        assertTrue(ready, "Service did not start within 30 s")
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `checks service with jetty jakarta engine`() {
        val jettyPort: Int = ServerSocket(0).use { it.localPort }
        val jettyProc = startService("jetty-jakarta", jettyPort, closeGracePeriodMillis, closeTimeoutMillis, requestBodyLimit)
        val ready = waitForLog(
            jettyProc.inputStream.bufferedReader(),
            text = "Server started",
            timeoutSec = 30,
        )
        assertTrue(ready, "Service did not start within 30 s")
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

internal fun ServiceGeneratorTest.generateService(): MockManifest {
    val settings: ObjectNode = ObjectNode.builder()
        .withMember("service", Node.from("$packageName#$serviceName"))
        .withMember(
            "package",
            ObjectNode.builder()
                .withMember("name", Node.from(packageName))
                .withMember("version", Node.from("1.0.0"))
                .build(),
        )
        .withMember(
            "build",
            ObjectNode.builder()
                .withMember("rootProject", true)
                .withMember("generateServiceProject", true)
                .withMember(
                    "optInAnnotations",
                    Node.arrayNode(
                        Node.from("aws.smithy.kotlin.runtime.InternalApi"),
                        Node.from("kotlinx.serialization.ExperimentalSerializationApi"),
                    ),
                )
                .build(),
        )
        .withMember(
            "serviceStub",
            ObjectNode.builder().withMember("framework", Node.from("ktor")).build(),
        )
        .build()

    val kotlinSettings = KotlinSettings.from(defaultModel, settings)
    val integrations: List<KotlinIntegration> = listOf()
    val manifest = MockManifest()
    val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model = defaultModel, rootNamespace = packageName, serviceName = serviceName, settings = kotlinSettings)
    val service = defaultModel.getShape(ShapeId.from("$packageName#$serviceName")).get().asServiceShape().get()
    val delegator = KotlinDelegator(kotlinSettings, defaultModel, manifest, provider, integrations)

    val generator = RpcV2Cbor()
    generator.apply {
        val ctx = ProtocolGenerator.GenerationContext(
            kotlinSettings,
            defaultModel,
            service,
            provider,
            integrations,
            protocol,
            delegator,
        )
        generator.generateProtocolClient(ctx)
        ctx.delegator.flushWriters()
    }

    val context: PluginContext = PluginContext.builder()
        .model(defaultModel)
        .fileManifest(manifest)
        .settings(settings)
        .build()
    KotlinCodegenPlugin().execute(context)
    return manifest
}

@OptIn(ExperimentalPathApi::class)
internal fun ServiceGeneratorTest.writeService(manifest: MockManifest) {
    manifest.writeFile("settings.gradle.kts", "rootProject.name = \"$serviceName\"")
    manifest.files.forEach { rel ->
        val target = projectDir.resolve(rel.toString().removePrefix("/"))
        Files.createDirectories(target.parent)
        Files.write(target, manifest.expectFileBytes(rel))
    }

    if (!Files.exists(projectDir.resolve("gradlew"))) {
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments(
                "wrapper",
                "--quiet",
                "--stacktrace",
            )
            .forwardOutput()
            .build()
    }
}

@OptIn(ExperimentalPathApi::class)
internal fun ServiceGeneratorTest.startService(
    engineFactory: String = "netty",
    port: Int = 8080,
    closeGracePeriodMillis: Long = 1000,
    closeTimeoutMillis: Long = 1000,
    requestBodyLimit: Long = 10L * 1024 * 1024,
): Process = ProcessBuilder(
    "./gradlew",
    "--no-daemon",
    "--quiet",
    "run",
    "--args=--engineFactory $engineFactory --port $port --closeGracePeriodMillis ${closeGracePeriodMillis.toInt()} --closeTimeoutMillis ${closeTimeoutMillis.toInt()} --requestBodyLimit $requestBodyLimit",
)
    .directory(projectDir.toFile())
    .redirectErrorStream(true)
    .start()

@OptIn(ExperimentalPathApi::class)
internal fun ServiceGeneratorTest.cleanupService(proc: Process) {
    val gracefulWindow = closeGracePeriodMillis + closeTimeoutMillis
    val okExitCodes = setOf(0, 143)
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
    } finally {
        proc.destroyForcibly()
    }
}

internal fun waitForLog(
    reader: BufferedReader,
    text: String,
    timeoutSec: Long,
): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
    while (System.nanoTime() < deadline) {
        val line = reader.readLine() ?: break
        println(line)
        if (line.contains(text)) return true
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
