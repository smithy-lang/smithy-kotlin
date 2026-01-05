/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen

import aws.smithy.kotlin.codegen.test.TestModelDefault
import aws.smithy.kotlin.codegen.test.toSmithyModel
import aws.smithy.kotlin.codegen.utils.dq
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.ArgumentsProvider
import org.junit.jupiter.params.provider.ArgumentsSource
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.support.ParameterDeclarations
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.knowledge.NullableIndex.CheckMode
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import java.util.stream.Stream
import kotlin.test.*

class KotlinSettingsTest {
    @Test
    fun `infers default service`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "example",
                    "version": "1.0.0"
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        assertEquals(ShapeId.from(TestModelDefault.SERVICE_SHAPE_ID), settings.service)
        assertEquals("example", settings.pkg.name)
        assertEquals("1.0.0", settings.pkg.version)
    }

    @Test
    fun `correctly reads rootProject var from build settings`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        assertTrue(settings.build.generateFullProject)
        assertTrue(settings.build.generateDefaultBuildFiles)
    }

    @Test
    fun `correctly reads generateBuildFiles var from build settings`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        assertFalse(settings.build.generateFullProject)
        assertFalse(settings.build.generateDefaultBuildFiles)
    }

    @Test
    fun `correctly reads optin annotations from build settings`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        val expected = listOf("foo", "bar")
        assertEquals(expected, settings.build.optInAnnotations)
    }

    @Test
    fun `throws exception with empty package name`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        assertFailsWith<CodegenException> {
            KotlinSettings.Companion.from(
                model,
                Node.parse(contents).expectObjectNode(),
            )
        }
    }

    @Test
    fun `throws exception with invalid package name`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        assertFailsWith<CodegenException> {
            KotlinSettings.Companion.from(
                model,
                Node.parse(contents).expectObjectNode(),
            )
        }
    }

    @Test
    fun `allows valid package name`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )
    }

    @Test
    fun `supports internal visibility`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "aws.sdk.kotlin.runtime.protocoltest.awsrestjson",
                    "version": "1.0.0"
                },
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                },
                "api": {
                    "visibility": "internal"
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        assertEquals(Visibility.INTERNAL, settings.api.visibility)
    }

    @Test
    fun `defaults to public visibility`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

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

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        assertEquals(Visibility.PUBLIC, settings.api.visibility)
    }

    @Test
    fun `supports public visibility`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "aws.sdk.kotlin.runtime.protocoltest.awsrestjson",
                    "version": "1.0.0"
                },
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                },
                "api": {
                    "visibility": "public"
                }
            }
        """.trimIndent()

        val settings = KotlinSettings.Companion.from(
            model,
            Node.parse(contents).expectObjectNode(),
        )

        assertEquals(Visibility.PUBLIC, settings.api.visibility)
    }

    @Test
    fun `throws on unsupported visibility values`() {
        val model = javaClass.getResource("simple-service.smithy")!!.toSmithyModel()

        val contents = """
            {
                "package": {
                    "name": "aws.sdk.kotlin.runtime.protocoltest.awsrestjson",
                    "version": "1.0.0"
                },
                "build": {
                    "optInAnnotations": ["foo", "bar"]
                },
                "api": {
                    "visibility": "I don't know, just make it visible"
                }
            }
        """.trimIndent()

        assertFailsWith<IllegalArgumentException> {
            KotlinSettings.Companion.from(
                model,
                Node.parse(contents).expectObjectNode(),
            )
        }
    }

    @ParameterizedTest(name = "{0} ==> {1}")
    @CsvSource(
        "client, CLIENT",
        "clientCareful, CLIENT_CAREFUL",
        "clientZeroValueV1, CLIENT_ZERO_VALUE_V1",
        "clientZeroValueV1NoInput, CLIENT_ZERO_VALUE_V1_NO_INPUT",
        "server, SERVER",
    )
    fun testNullabilityCheckMode(pluginSetting: String, expectedEnumString: String) {
        val expected = CheckMode.valueOf(expectedEnumString)
        val contents = """
            {
                "nullabilityCheckMode": "$pluginSetting"
            }
        """.trimIndent()
        val apiSettings = ApiSettings.Companion.fromNode(Node.parse(contents).asObjectNode())

        assertEquals(expected, apiSettings.nullabilityCheckMode)
    }

    @ParameterizedTest(name = "{0} ==> {1}")
    @CsvSource(
        "always, ALWAYS",
        "whenDifferent, WHEN_DIFFERENT",
    )
    fun testDefaultValueSerializationMode(pluginSetting: String, expectedEnumString: String) {
        val expected = DefaultValueSerializationMode.valueOf(expectedEnumString)
        val contents = """
            {
                "defaultValueSerializationMode": "$pluginSetting"
            }
        """.trimIndent()
        val apiSettings = ApiSettings.Companion.fromNode(Node.parse(contents).asObjectNode())

        assertEquals(expected, apiSettings.defaultValueSerializationMode)
    }

    @ParameterizedTest
    @ArgumentsSource(TestProtocolSelectionArgumentProvider::class)
    fun testProtocolSelection(
        protocolPriorityCsv: String,
        serviceProtocolsCsv: String,
        expectedProtocolName: String?,
    ) {
        val serviceProtocols = serviceProtocolsCsv.csvToProtocolList()
        val serviceProtocolImports = serviceProtocols.joinToString("\n") { "use $it" }
        val serviceProtocolTraits = serviceProtocols.joinToString("\n") { "@${it.name}" }
        val supportedProtocols = protocolPriorityCsv.csvToProtocolList().toSet()
        val protocolPriorityList = supportedProtocols.joinToString(", ") { it.toString().dq() }

        val model = """
            |namespace com.test
            |
            |$serviceProtocolImports
            |
            |$serviceProtocolTraits
            |@xmlNamespace(uri: "http://test.com") // required for @awsQuery
            |service Test {
            |    version: "1.0.0"
            |}
        """.trimMargin().toSmithyModel()
        val service = model.serviceShapes.single()
        val serviceIndex = ServiceIndex.of(model)

        val contents = """
            {
                "package": {
                    "name": "name",
                    "version": "1.0.0"
                },
                "api": {
                    "protocolResolutionPriority": [ $protocolPriorityList ]
                }
            }
        """.trimIndent()
        val settings = KotlinSettings.Companion.from(model, Node.parse(contents).expectObjectNode())

        val expectedProtocol = expectedProtocolName?.nameToProtocol()
        val actualProtocol = runCatching {
            settings.resolveServiceProtocol(serviceIndex, service, supportedProtocols)
        }.getOrElse { null }

        assertEquals(expectedProtocol, actualProtocol)
    }
}

/**
 * A junit [ArgumentsProvider] which supplies protocol selection parameterized test values sourced from the Smithy RPCv2
 * CBOR Support SEP § Smithy protocol selection tests.
 */
class TestProtocolSelectionArgumentProvider : ArgumentsProvider {
    companion object {
        private const val ALL_PROTOCOLS = "rpcv2Cbor, awsJson1_0, awsJson1_1, restJson1, restXml, awsQuery, ec2Query"
        private const val NO_CBOR = "awsJson1_0, awsJson1_1, restJson1, restXml, awsQuery, ec2Query"
    }

    override fun provideArguments(
        parameters: ParameterDeclarations?,
        context: ExtensionContext?,
    ): Stream<out Arguments> = Stream.of(
        Arguments.of(
            ALL_PROTOCOLS,
            "rpcv2Cbor, awsJson1_0",
            "rpcv2Cbor",
        ),
        Arguments.of(
            ALL_PROTOCOLS,
            "rpcv2Cbor",
            "rpcv2Cbor",
        ),
        Arguments.of(
            ALL_PROTOCOLS,
            "rpcv2Cbor, awsJson1_0, awsQuery",
            "rpcv2Cbor",
        ),
        Arguments.of(
            ALL_PROTOCOLS,
            "awsJson1_0, awsQuery",
            "awsJson1_0",
        ),
        Arguments.of(
            ALL_PROTOCOLS,
            "awsQuery",
            "awsQuery",
        ),
        Arguments.of(
            NO_CBOR,
            "rpcv2Cbor, awsJson1_0",
            "awsJson1_0",
        ),
        Arguments.of(
            NO_CBOR,
            "rpcv2Cbor",
            null,
        ),
        Arguments.of(
            NO_CBOR,
            "rpcv2Cbor, awsJson1_0, awsQuery",
            "awsJson1_0",
        ),
        Arguments.of(
            NO_CBOR,
            "awsJson1_0, awsQuery",
            "awsJson1_0",
        ),
        Arguments.of(
            NO_CBOR,
            "awsQuery",
            "awsQuery",
        ),
    )
}

private val allProtocols = ApiSettings().protocolResolutionPriority
private fun String.nameToProtocol() = allProtocols.single { protocol -> protocol.name == this }
private fun String.csvToProtocolList() = split(",").map(String::trim).map(String::nameToProtocol)
