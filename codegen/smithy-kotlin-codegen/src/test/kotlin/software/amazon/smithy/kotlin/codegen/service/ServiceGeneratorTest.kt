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
data class AuthTestResponse(
    val output1: String? = null,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ServiceGeneratorTest {
    val defaultModel = loadModelFromResource("service-generator-test.smithy")
    val serviceName = "ServiceGeneratorTest"
    val packageName = "com.test"
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

        val bearerValidation = """
            package $packageName.auth
            
            public fun bearerValidation(token: String): UserPrincipal? {
                if (token == "correctToken") return UserPrincipal("Authenticated User") else return null
            }
        """.trimIndent()
        manifest.writeFile("src/main/kotlin/$packagePath/auth/Validation.kt", bearerValidation)
        proc = startService(manifest)

        val ready = waitForLog(
            proc.inputStream.bufferedReader(),
            text = "Server started",
            timeoutSec = 10,
        )
        assertTrue(ready, "Service did not start within 10 s")
    }

    @AfterAll
    fun shutdown() = cleanupService(proc)

    @Test
    fun `it generates service and all necessary files`() {
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
    fun `service responds to POST request`() {
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
    fun `check wrong content type`() {
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
    fun `check wrong accept type`() {
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
    fun `check authentication with correct bearer token`() {
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
    fun `check authentication with wrong bearer token`() {
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
    fun `check authentication without bearer token`() {
        // FIXME
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
    fun `check malformed input`() {
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
internal fun ServiceGeneratorTest.startService(manifest: MockManifest): Process {
    manifest.files.forEach { rel ->
        val target = projectDir.resolve(rel.toString().removePrefix("/"))
        Files.createDirectories(target.parent)
        Files.write(target, manifest.expectFileBytes(rel))
    }

    if (!Files.exists(projectDir.resolve("gradlew"))) {
        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("wrapper", "--quiet")
            .build()
    }

    return ProcessBuilder("./gradlew", "--quiet", "run", "--args=--port $port")
        .directory(projectDir.toFile())
        .redirectErrorStream(true)
        .start()
}

@OptIn(ExperimentalPathApi::class)
internal fun ServiceGeneratorTest.cleanupService(proc: Process) {
    proc.destroyForcibly()
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
