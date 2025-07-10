/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import kotlin.test.Test
import kotlin.test.assertTrue
import java.nio.file.Path
import java.nio.file.Files
import java.io.BufferedReader
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir
import kotlin.io.path.name
import kotlin.math.max


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
                .build()
        )
        .withMember(
            "build",
            ObjectNode.builder()
                .withMember("rootProject", true)
                .withMember("generateServiceProject", true)
                .withMember("optInAnnotations", Node.arrayNode(
                    Node.from("aws.smithy.kotlin.runtime.InternalApi"),
                    Node.from("kotlinx.serialization.ExperimentalSerializationApi"),
                ))
                .build()
        )
        .withMember(
            "serviceStub",
            ObjectNode.builder().withMember("framework", Node.from("ktor")).build()
        )
        .build()

    val manifest = MockManifest()
    val context: PluginContext = PluginContext.builder()
        .model(defaultModel)
        .fileManifest(manifest)
        .settings(settings)
        .build()

    init {
        KotlinCodegenPlugin().execute(context)
    }

//    @Test
    fun `it generates service and all necessary files`() {
        assertTrue(manifest.hasFile("build.gradle.kts"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/Main.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/Routing.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/config/ServiceFrameworkConfig.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/framework/ServiceFramework.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/model/GetTestRequest.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/model/GetTestResponse.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/operations/GetTestOperation.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/plugins/ContentTypeGuard.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/plugins/ErrorHandler.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/utils/Logging.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/utils/serde/GetTestOperationSerializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/com/test/test/utils/serde/GetTestOperationDeserializer.kt"))
    }

    @OptIn(ExperimentalPathApi::class)
//    @Test
    fun `generated service runs successfully`() {
        manifest.files.forEach { rel ->
            val target = projectDir.resolve(rel.toString().removePrefix("/"))
            Files.createDirectories(target.parent)
            Files.write(target, manifest.expectFileBytes(rel))
        }

        printDirectoryTree(projectDir)

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

        try {
            val ready = waitForLog(proc.inputStream.bufferedReader(),
                text = "Engine started",
                timeoutSec = 20)
            assertTrue(ready, "Service did not start within 20 s")
        } finally {
            proc.destroyForcibly()
            proc.waitFor(5, TimeUnit.SECONDS)
        }

    }

    private fun waitForLog(reader: BufferedReader,
                           text: String,
                           timeoutSec: Long): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSec)
        while (System.nanoTime() < deadline) {
            val line = reader.readLine() ?: break
            println("---------------")
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
}