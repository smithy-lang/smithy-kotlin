/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.assertBalancedBracesAndParens
import software.amazon.smithy.kotlin.codegen.test.formatForTest
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.rulesengine.language.EndpointRuleset
import kotlin.test.*

class EndpointParametersGeneratorTest {
    private val generatedClass: String

    init {
        val rules = createTestRulesetParams(
            """
                {
                    "DocumentedField": {
                        "type": "String",
                        "required": false,
                        "documentation": "A documented field. Has \"a quoted phrase\"."
                    },
                    "StringField": {
                        "type": "String",
                        "required": false
                    },
                    "BooleanField": {
                        "type": "Boolean",
                        "required": false
                    },
                    "RequiredStringField": {
                        "type": "String",
                        "required": true
                    },
                    "RequiredBooleanField": {
                        "type": "Boolean",
                        "required": true
                    },
                    "DefaultedStringField": {
                        "type": "String",
                        "required": true,
                        "builtIn": "SDK::DefaultedStringField",
                        "default": "default_string"
                    },
                    "DefaultedBooleanField": {
                        "type": "Boolean",
                        "required": true,
                        "builtIn": "SDK::DefaultedBooleanField",
                        "default": true
                    },
                    "DeprecatedField": {
                        "type": "String",
                        "required": false,
                        "deprecated": {}
                    },
                    "DeprecatedMessageField": {
                        "type": "String",
                        "required": false,
                        "deprecated": {
                            "message": "arbitrary message about deprecation, has \"a quoted phrase\""
                        }
                    }
                }
            """,
        )

        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EndpointParametersGenerator(writer, rules).render()

        generatedClass = writer.toString()
    }

    private fun createTestRulesetParams(paramsJson: String = "{}"): EndpointRuleset =
        EndpointRuleset.fromNode(
            Node.parse(
                """
                {
                    "version": "1.1",
                    "parameters": $paramsJson,
                    "rules": []
                }
            """,
            ),
        )

    @Test
    fun testPackageDecl() {
        assertContains(generatedClass, "package ${TestModelDefault.NAMESPACE}")
    }

    @Test
    fun testBalancedSyntax() {
        generatedClass.assertBalancedBracesAndParens()
    }

    @Test
    fun testDocumentation() {
        val expected = """
            /**
             * The set of values necessary for endpoint resolution.
             * @property documentedField A documented field. Has "a quoted phrase".
             * @property stringField
             * @property booleanField
             * @property requiredStringField
             * @property requiredBooleanField
             * @property defaultedStringField
             * @property defaultedBooleanField
             * @property deprecatedField
             * @property deprecatedMessageField
             */
        """.formatForTest(indent = "")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testConstructor() {
        val expected = """
            public data class EndpointParameters(
                public val documentedField: String? = null,
                public val stringField: String? = null,
                public val booleanField: Boolean? = null,
                public val requiredStringField: String? = null,
                public val requiredBooleanField: Boolean? = null,
                public val defaultedStringField: String? = "default_string",
                public val defaultedBooleanField: Boolean? = true,
                @Deprecated("This field is deprecated and no longer recommended for use.")
                public val deprecatedField: String? = null,
                @Deprecated("arbitrary message about deprecation, has \"a quoted phrase\"")
                public val deprecatedMessageField: String? = null,
            )
        """.formatForTest(indent = "")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testCompanionObject() {
        val expected = """
            public companion object {
                public operator fun invoke(block: Builder.() -> Unit): EndpointParameters = Builder().apply(block).build()
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testInit() {
        val expected = """
            init {
                requireNotNull(requiredStringField) { "endpoint provider parameter requiredStringField is required" }
                requireNotNull(requiredBooleanField) { "endpoint provider parameter requiredBooleanField is required" }
                requireNotNull(defaultedStringField) { "endpoint provider parameter defaultedStringField is required" }
                requireNotNull(defaultedBooleanField) { "endpoint provider parameter defaultedBooleanField is required" }
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testBuilder() {
        val expected = """
            public class Builder internal constructor() {
                /**
                 * A documented field. Has "a quoted phrase".
                 */
                public var documentedField: String? = null

                public var stringField: String? = null

                public var booleanField: Boolean? = null

                public var requiredStringField: String? = null

                public var requiredBooleanField: Boolean? = null

                public var defaultedStringField: String? = "default_string"

                public var defaultedBooleanField: Boolean? = true

                @Deprecated("This field is deprecated and no longer recommended for use.")
                public var deprecatedField: String? = null

                @Deprecated("arbitrary message about deprecation, has \"a quoted phrase\"")
                public var deprecatedMessageField: String? = null

                public fun build(): EndpointParameters {
                    return EndpointParameters(
                        documentedField,
                        stringField,
                        booleanField,
                        requiredStringField,
                        requiredBooleanField,
                        defaultedStringField,
                        defaultedBooleanField,
                        @Suppress("DEPRECATION")deprecatedField,
                        @Suppress("DEPRECATION")deprecatedMessageField,
                    )
                }
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }
}
