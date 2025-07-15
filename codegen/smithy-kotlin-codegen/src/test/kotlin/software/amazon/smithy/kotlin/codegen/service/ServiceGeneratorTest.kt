/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.service

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import org.gradle.testkit.runner.GradleRunner
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
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

class ServiceGeneratorTest {
    private val defaultModel = loadModelFromResource("service-generator-test.smithy")

    @TempDir
    lateinit var projectDir: Path

    val settings: ObjectNode = ObjectNode.builder()
        .withMember("service", Node.from("com.test#ServiceGeneratorTest"))
        .withMember(
            "package",
            ObjectNode.builder()
                .withMember("name", Node.from("com.test.test"))
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

    val serviceName = "ServiceGeneratorTest"
    val packageName = "com.test"

    fun generateService(): MockManifest {
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
    fun startService(manifest: MockManifest): Process {
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

        val proc = ProcessBuilder("./gradlew", "--quiet", "run")
            .directory(projectDir.toFile())
            .redirectErrorStream(true)
            .start()

        return proc
    }

    @OptIn(ExperimentalPathApi::class)
    private fun cleanupService(proc: Process) {
        proc.destroyForcibly()
    }

    private fun waitForLog(
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

    @OptIn(ExperimentalPathApi::class)
    fun printDirectoryTree(root: Path) {
        // Walk the directory, sort so parents come before children,
        // then pretty-print with Unicode branches.
        Files.walk(root)
            .sorted()
            .forEach { path ->
                val rel = root.relativize(path)
                val depth = rel.nameCount
                val branch = if (Files.isDirectory(path)) "└── " else "├── "
                val indent = "    ".repeat(max(0, depth - 1))
                println("$indent$branch${path.fileName}")
            }
    }

    @Test
    fun `it generates service and all necessary files`() {
        val manifest = generateService()

        assertTrue(manifest.hasFile("build.gradle.kts"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/Main.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/Routing.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/config/ServiceFrameworkConfig.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/framework/ServiceFramework.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/plugins/ContentTypeGuard.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/plugins/ErrorHandler.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/utils/Logging.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/auth/Authentication.kt"))

        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/model/PostTestRequest.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/model/PostTestResponse.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/serde/PostTestOperationSerializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/serde/PostTestOperationDeserializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/operations/PostTestOperation.kt"))
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `generated service runs successfully`() {
        val manifest = generateService()
        val proc = startService(manifest)
        try {
            val ready = waitForLog(
                proc.inputStream.bufferedReader(),
                text = "Server started",
                timeoutSec = 5,
            )
            assertTrue(ready, "Service did not start within 5 s")
        } finally {
            cleanupService(proc)
        }
    }

    @Test
    @OptIn(ExperimentalPathApi::class, ExperimentalSerializationApi::class)
    fun `service responds to POST request`() {
        val manifest = generateService()
        println(manifest.getFileString("src/main/kotlin/com/test/test/Routing.kt"))
        val postTestOperation = """
            package $packageName.test.operations

            import $packageName.test.model.PostTestRequest
            import $packageName.test.model.PostTestResponse

            public fun handlePostTestRequest(req: PostTestRequest): PostTestResponse {
                val response = PostTestResponse.Builder()
                val input1 = req.input1 ?: ""
                val input2 = req.input2 ?: 0
                response.output1 = input1 + " world!"
                response.output2 = input2 + 1
                return response.build()
            }
        """.trimIndent()
        manifest.writeFile("src/main/kotlin/com/test/test/operations/PostTestOperation.kt", postTestOperation.toString())

        val proc = startService(manifest)
        try {
            val ready = waitForLog(
                proc.inputStream.bufferedReader(),
                text = "Server started",
                timeoutSec = 5,
            )
            assertTrue(ready, "Service did not start within 5 s")

            val cbor = Cbor { }
            val input1 = "Hello"
            val input2 = 617
            val requestBytes = cbor.encodeToByteArray(
                PostTestRequest.serializer(),
                PostTestRequest(input1, input2),
            )

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:8080/post"))
                .header("Content-Type", "application/cbor")
                .header("Accept", "application/cbor")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBytes))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(201, response.statusCode(), "Expected 201 OK")

            val body = cbor.decodeFromByteArray(
                PostTestResponse.serializer(),
                response.body(),
            )

            assertEquals("Hello world!", body.output1)
            assertEquals(input2 + 1, body.output2)
        } finally {
            cleanupService(proc)
        }
    }
}
