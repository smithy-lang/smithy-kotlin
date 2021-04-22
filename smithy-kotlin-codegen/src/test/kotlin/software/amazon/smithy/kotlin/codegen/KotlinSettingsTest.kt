/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId

class KotlinSettingsTest {
    @Test
    fun `infers default service`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "example",
                    "version": "1.0.0"
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.from(
            model,
            Node.parse(contents).expectObjectNode()
        )

        assertEquals(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID), settings.service)
        assertEquals("example", settings.pkg.name)
        assertEquals("1.0.0", settings.pkg.version)
    }

    @Test
    fun `correctly reads rootProject var from build settings`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "example",
                    "version": "1.0.0"
                },
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

    @Test
    fun `correctly reads generateBuildFiles var from build settings`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "example",
                    "version": "1.0.0"
                },
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

    @Test
    fun `correctly reads optin annotations from build settings`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "example",
                    "version": "1.0.0"
                },
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

    @Test
    fun `throws exception with empty package name`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "",
                    "version": "1.0.0"
                },
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                }
            }
        """.trimIndent()

        assertThrows<CodegenException> {
            KotlinSettings.from(
                model,
                Node.parse(contents).expectObjectNode()
            )
        }
    }

    @Test
    fun `throws exception with invalid package name`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "rds-data",
                    "version": "1.0.0"
                },
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                }
            }
        """.trimIndent()

        assertThrows<CodegenException> {
            KotlinSettings.from(
                model,
                Node.parse(contents).expectObjectNode()
            )
        }
    }

    @Test
    fun `allows valid package name`() {
        val model = javaClass.getResource("simple-service.smithy").toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "aws.sdk.kotlin.runtime.protocoltest.awsrestjson",
                    "version": "1.0.0"
                },
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                }
            }
        """.trimIndent()

        KotlinSettings.from(
            model,
            Node.parse(contents).expectObjectNode()
        )
    }
}
