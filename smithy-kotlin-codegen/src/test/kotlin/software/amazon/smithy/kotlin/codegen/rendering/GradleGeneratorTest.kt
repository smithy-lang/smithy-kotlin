/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency.Companion.CORE
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.model.node.Node

class GradleGeneratorTest {

    @Test
    fun `it writes dependencies`() {
        val model = loadModelFromResource("simple-service.smithy")

        val settings = KotlinSettings.from(
            model,
            Node.objectNodeBuilder()
                .withMember(
                    "package",
                    Node.objectNode()
                        .withMember("name", Node.from("example"))
                        .withMember("version", Node.from("1.0.0"))
                )
                .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(false)).build())
                .build()
        )

        val manifest = MockManifest()
        val dependencies = listOf(KotlinDependency.CORE)
        writeGradleBuild(settings, manifest, dependencies)
        val contents = manifest.getFileString("build.gradle.kts").get()
        val expected = """
            api("$RUNTIME_GROUP:${CORE.artifact}:$RUNTIME_VERSION")
        """.trimIndent()

        contents.shouldContain(expected)
    }

    @Test
    fun `it writes full project`() {
        val model = loadModelFromResource("simple-service.smithy")

        val settings = KotlinSettings.from(
            model,
            Node.objectNodeBuilder()
                .withMember(
                    "package",
                    Node.objectNode()
                        .withMember("name", Node.from("example"))
                        .withMember("version", Node.from("1.0.0"))
                )
                .withMember("build", Node.objectNodeBuilder().withMember("rootProject", Node.from(true)).build())
                .build()
        )

        val manifest = MockManifest()
        val dependencies = listOf(KotlinDependency.CORE)
        writeGradleBuild(settings, manifest, dependencies)
        val contents = manifest.getFileString("build.gradle.kts").get()
        val expectedRepositories = """
        repositories {
            mavenLocal()
            mavenCentral()
        }
        """.trimIndent()
        val expectedVersion = """
            kotlin("jvm") version
        """.trimIndent()

        contents.shouldContain(expectedRepositories)
        contents.shouldContain(expectedVersion)
    }
}
