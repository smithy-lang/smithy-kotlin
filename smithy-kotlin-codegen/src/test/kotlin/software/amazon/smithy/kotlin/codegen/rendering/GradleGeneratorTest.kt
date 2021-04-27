/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node

class GradleGeneratorTest {

    private fun getModel(): Model {
        return javaClass
            .classLoader
            .getResource("software/amazon/smithy/kotlin/codegen/simple-service.smithy")!!
            .toSmithyModel()
    }

    @Test
    fun `it writes dependencies`() {
        val model = getModel()

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
        val dependencies = listOf(KotlinDependency.CLIENT_RT_CORE)
        writeGradleBuild(settings, manifest, dependencies)
        val contents = manifest.getFileString("build.gradle.kts").get()
        val expected = """
            api("$CLIENT_RT_GROUP:client-rt-core:$CLIENT_RT_VERSION")
        """.trimIndent()

        contents.shouldContain(expected)
    }

    @Test
    fun `it writes full project`() {
        val model = getModel()

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
        val dependencies = listOf(KotlinDependency.CLIENT_RT_CORE)
        writeGradleBuild(settings, manifest, dependencies)
        val contents = manifest.getFileString("build.gradle.kts").get()
        val expectedRepositories = """
        repositories {
            mavenLocal()
            mavenCentral()
            jcenter()
        }
        """.trimIndent()
        val expectedVersion = """
            kotlin("jvm") version
        """.trimIndent()

        contents.shouldContain(expectedRepositories)
        contents.shouldContain(expectedVersion)
    }
}
