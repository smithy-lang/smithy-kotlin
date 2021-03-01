/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId

class KotlinSettingsTest {
    @Test fun `infers default service`() {
        val model = javaClass.getResource("simple-service.smithy").asSmithy()

        val contents = """
            {
                "module": "example",
                "moduleVersion": "1.0.0"
            }
        """.trimIndent()

        val settings = KotlinSettings.from(
            model,
            Node.parse(contents).expectObjectNode()
        )

        assertEquals(ShapeId.from("smithy.example#Example"), settings.service)
        assertEquals("example", settings.pkg.name)
        assertEquals("1.0.0", settings.pkg.version)
    }

    @Test fun `correctly reads rootProject var from build settings`() {
        val model = javaClass.getResource("simple-service.smithy").asSmithy()

        val contents = """
            {
                "module": "example",
                "moduleVersion": "1.0.0",
                "build": {
                    "rootProject": true
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.from(
            model,
            Node.parse(contents).expectObjectNode()
        )

        assertTrue(settings.build.generateFullProject)
        assertTrue(settings.build.generateDefaultBuildFiles)
    }

    @Test fun `correctly reads generateBuildFiles var from build settings`() {
        val model = javaClass.getResource("simple-service.smithy").asSmithy()

        val contents = """
            {
                "module": "example",
                "moduleVersion": "1.0.0",
                "build": {
                    "generateDefaultBuildFiles": false
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.from(
            model,
            Node.parse(contents).expectObjectNode()
        )

        assertFalse(settings.build.generateFullProject)
        assertFalse(settings.build.generateDefaultBuildFiles)
    }

    @Test fun `correctly reads optin annotations from build settings`() {
        val model = javaClass.getResource("simple-service.smithy").asSmithy()

        val contents = """
            {
                "module": "example",
                "moduleVersion": "1.0.0",
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.from(
            model,
            Node.parse(contents).expectObjectNode()
        )

        val expected = listOf("foo", "bar")
        assertEquals(expected, settings.build.optInAnnotations)
    }
}
