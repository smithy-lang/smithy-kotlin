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
                    },
                    "DocumentedDeprecatedField": {
                        "type": "String",
                        "required": false,
                        "documentation": "description about a field but it's deprecated",
                        "deprecated": {
                            "message": "it's deprecated"
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
             */
        """.formatForTest(indent = "")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testConstructor() {
        val expected = """
            public class EndpointParameters private constructor(builder: Builder)
        """.formatForTest(indent = "")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testFields() {
        val expected = """
            /**
             * A documented field. Has "a quoted phrase".
             */
            public val documentedField: String? = builder.documentedField

            public val stringField: String? = builder.stringField

            public val booleanField: Boolean? = builder.booleanField

            public val requiredStringField: String? = builder.requiredStringField

            public val requiredBooleanField: Boolean? = builder.requiredBooleanField

            public val defaultedStringField: String? = builder.defaultedStringField

            public val defaultedBooleanField: Boolean? = builder.defaultedBooleanField

            @Suppress("DEPRECATION")
            @Deprecated("This field is deprecated and no longer recommended for use.")
            public val deprecatedField: String? = builder.deprecatedField

            @Suppress("DEPRECATION")
            @Deprecated("arbitrary message about deprecation, has \"a quoted phrase\"")
            public val deprecatedMessageField: String? = builder.deprecatedMessageField

            @Suppress("DEPRECATION")
            /**
             * description about a field but it's deprecated
             */
            @Deprecated("it's deprecated")
            public val documentedDeprecatedField: String? = builder.documentedDeprecatedField
        """.formatForTest()
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
    fun testEquals() {
        val expected = """
            public override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is EndpointParameters) return false
                if (this.documentedField != other.documentedField) return false
                if (this.stringField != other.stringField) return false
                if (this.booleanField != other.booleanField) return false
                if (this.requiredStringField != other.requiredStringField) return false
                if (this.requiredBooleanField != other.requiredBooleanField) return false
                if (this.defaultedStringField != other.defaultedStringField) return false
                if (this.defaultedBooleanField != other.defaultedBooleanField) return false
                @Suppress("DEPRECATION")
                if (this.deprecatedField != other.deprecatedField) return false
                @Suppress("DEPRECATION")
                if (this.deprecatedMessageField != other.deprecatedMessageField) return false
                @Suppress("DEPRECATION")
                if (this.documentedDeprecatedField != other.documentedDeprecatedField) return false
                return true
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testHashCode() {
        val expected = """
            public override fun hashCode(): Int {
                var result = documentedField?.hashCode() ?: 0
                result = 31 * result + (stringField?.hashCode() ?: 0)
                result = 31 * result + (booleanField?.hashCode() ?: 0)
                result = 31 * result + (requiredStringField?.hashCode() ?: 0)
                result = 31 * result + (requiredBooleanField?.hashCode() ?: 0)
                result = 31 * result + (defaultedStringField?.hashCode() ?: 0)
                result = 31 * result + (defaultedBooleanField?.hashCode() ?: 0)
                @Suppress("DEPRECATION")
                result = 31 * result + (deprecatedField?.hashCode() ?: 0)
                @Suppress("DEPRECATION")
                result = 31 * result + (deprecatedMessageField?.hashCode() ?: 0)
                @Suppress("DEPRECATION")
                result = 31 * result + (documentedDeprecatedField?.hashCode() ?: 0)
                return result
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testToString() {
        val expected = """
            public override fun toString(): String = buildString {
                append("EndpointParameters(")
                append("documentedField=${'$'}documentedField,")
                append("stringField=${'$'}stringField,")
                append("booleanField=${'$'}booleanField,")
                append("requiredStringField=${'$'}requiredStringField,")
                append("requiredBooleanField=${'$'}requiredBooleanField,")
                append("defaultedStringField=${'$'}defaultedStringField,")
                append("defaultedBooleanField=${'$'}defaultedBooleanField,")
                @Suppress("DEPRECATION")
                append("deprecatedField=${'$'}deprecatedField,")
                @Suppress("DEPRECATION")
                append("deprecatedMessageField=${'$'}deprecatedMessageField,")
                @Suppress("DEPRECATION")
                append("documentedDeprecatedField=${'$'}documentedDeprecatedField")
                append(")")
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testCopy() {
        val expected = """
            public fun copy(block: Builder.() -> Unit = {}): EndpointParameters {
                return Builder().apply {
                    documentedField = this@EndpointParameters.documentedField
                    stringField = this@EndpointParameters.stringField
                    booleanField = this@EndpointParameters.booleanField
                    requiredStringField = this@EndpointParameters.requiredStringField
                    requiredBooleanField = this@EndpointParameters.requiredBooleanField
                    defaultedStringField = this@EndpointParameters.defaultedStringField
                    defaultedBooleanField = this@EndpointParameters.defaultedBooleanField
                    @Suppress("DEPRECATION")
                    deprecatedField = this@EndpointParameters.deprecatedField
                    @Suppress("DEPRECATION")
                    deprecatedMessageField = this@EndpointParameters.deprecatedMessageField
                    @Suppress("DEPRECATION")
                    documentedDeprecatedField = this@EndpointParameters.documentedDeprecatedField
                }
                .apply(block).build()
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

                /**
                 * description about a field but it's deprecated
                 */
                @Deprecated("it's deprecated")
                public var documentedDeprecatedField: String? = null

                internal fun build(): EndpointParameters = EndpointParameters(this)
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }
}
