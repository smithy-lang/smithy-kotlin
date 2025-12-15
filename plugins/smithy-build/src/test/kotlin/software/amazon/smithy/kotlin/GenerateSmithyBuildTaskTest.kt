/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin

import org.gradle.kotlin.dsl.register
import org.gradle.testfixtures.ProjectBuilder
import software.amazon.smithy.kotlin.dsl.SmithyBuildPluginSettings
import software.amazon.smithy.kotlin.dsl.SmithyProjection
import software.amazon.smithy.kotlin.tasks.GenerateSmithyBuild
import software.amazon.smithy.kotlin.tasks.json
import software.amazon.smithy.model.node.Node
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateSmithyBuildTaskTest {
    @Test
    fun testDefaults() {
        val testProj = ProjectBuilder.builder().build()
        val task = testProj.tasks.register<GenerateSmithyBuild>("generateSmithyBuild").get()
        assertEquals(task.generatedOutput.get().asFile.path, testProj.layout.buildDirectory.file("smithy-build.json").get().asFile.path)
    }

    @Test
    fun testGeneratedBuild() {
        val testProj = ProjectBuilder.builder().build()
        val testPlugin = object : SmithyBuildPluginSettings {
            override val pluginName: String = "plugin1"

            override fun toNode(): Node = Node.objectNodeBuilder()
                .withMember("key1", "value1")
                .build()
        }

        val smithyProjections = listOf(
            SmithyProjection("foo").apply {
                imports = listOf("i1")
                sources = listOf("s1")
                transforms = listOf("""{ "key": "value" }""")
                plugins["plugin1"] = testPlugin
            },
        )
        val task = testProj.tasks.register<GenerateSmithyBuild>(
            "generateSmithyBuild",
        ) {
            smithyBuildConfig.set(smithyProjections.json)
        }.get()

        task.generateSmithyBuild()
        assertTrue(task.generatedOutput.get().asFile.exists())
        val contents = task
            .generatedOutput
            .get()
            .asFile
            .readText()
            .replace("\r\n", "\n") // For windows
        val expected = """
            {
                "version": "1.0",
                "projections": {
                    "foo": {
                        "sources": [
                            "s1"
                        ],
                        "imports": [
                            "i1"
                        ],
                        "transforms": [
                            {
                                "key": "value"
                            }
                        ],
                        "plugins": {
                            "plugin1": {
                                "key1": "value1"
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        assertEquals(expected, contents)
    }
}
