/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
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

        val model =
            """
            namespace com.test
            service Test {
                version: "1.0.0",
            }
        """.toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        EndpointParametersGenerator(testCtx.generationCtx, rules, writer).render()

        generatedClass = writer.toString()
    }

    private fun createTestRulesetParams(paramsJson: String = "{}"): EndpointRuleSet =
        EndpointRuleSet.fromNode(
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
            public class TestEndpointParameters private constructor(builder: Builder)
        """.formatForTest(indent = "")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testFields() {
        val expected = """
            public val booleanField: Boolean? = builder.booleanField
         
            public val defaultedBooleanField: Boolean? = requireNotNull(builder.defaultedBooleanField) { "endpoint provider parameter #defaultedBooleanField is required" }
         
            public val defaultedStringField: String? = requireNotNull(builder.defaultedStringField) { "endpoint provider parameter #defaultedStringField is required" }
         
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
         
            /**
             * A documented field. Has "a quoted phrase".
             */
            public val documentedField: String? = builder.documentedField
         
            public val requiredBooleanField: Boolean? = requireNotNull(builder.requiredBooleanField) { "endpoint provider parameter #requiredBooleanField is required" }
         
            public val requiredStringField: String? = requireNotNull(builder.requiredStringField) { "endpoint provider parameter #requiredStringField is required" }
         
            public val stringField: String? = builder.stringField
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testCompanionObject() {
        val expected = """
            public companion object {
                public inline operator fun invoke(block: Builder.() -> Unit): TestEndpointParameters = Builder().apply(block).build()
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testEquals() {
        val expected = """
            public override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is TestEndpointParameters) return false
                if (this.booleanField != other.booleanField) return false
                if (this.defaultedBooleanField != other.defaultedBooleanField) return false
                if (this.defaultedStringField != other.defaultedStringField) return false
                @Suppress("DEPRECATION")
                if (this.deprecatedField != other.deprecatedField) return false
                @Suppress("DEPRECATION")
                if (this.deprecatedMessageField != other.deprecatedMessageField) return false
                @Suppress("DEPRECATION")
                if (this.documentedDeprecatedField != other.documentedDeprecatedField) return false
                if (this.documentedField != other.documentedField) return false
                if (this.requiredBooleanField != other.requiredBooleanField) return false
                if (this.requiredStringField != other.requiredStringField) return false
                if (this.stringField != other.stringField) return false
                return true
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testHashCode() {
        val expected = """
            public override fun hashCode(): Int {
                var result = booleanField?.hashCode() ?: 0
                result = 31 * result + (defaultedBooleanField?.hashCode() ?: 0)
                result = 31 * result + (defaultedStringField?.hashCode() ?: 0)
                @Suppress("DEPRECATION")
                result = 31 * result + (deprecatedField?.hashCode() ?: 0)
                @Suppress("DEPRECATION")
                result = 31 * result + (deprecatedMessageField?.hashCode() ?: 0)
                @Suppress("DEPRECATION")
                result = 31 * result + (documentedDeprecatedField?.hashCode() ?: 0)
                result = 31 * result + (documentedField?.hashCode() ?: 0)
                result = 31 * result + (requiredBooleanField?.hashCode() ?: 0)
                result = 31 * result + (requiredStringField?.hashCode() ?: 0)
                result = 31 * result + (stringField?.hashCode() ?: 0)
                return result
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testToString() {
        val expected = """
            public override fun toString(): String = buildString {
                append("TestEndpointParameters(")
                append("booleanField=${'$'}booleanField,")
                append("defaultedBooleanField=${'$'}defaultedBooleanField,")
                append("defaultedStringField=${'$'}defaultedStringField,")
                @Suppress("DEPRECATION")
                append("deprecatedField=${'$'}deprecatedField,")
                @Suppress("DEPRECATION")
                append("deprecatedMessageField=${'$'}deprecatedMessageField,")
                @Suppress("DEPRECATION")
                append("documentedDeprecatedField=${'$'}documentedDeprecatedField,")
                append("documentedField=${'$'}documentedField,")
                append("requiredBooleanField=${'$'}requiredBooleanField,")
                append("requiredStringField=${'$'}requiredStringField,")
                append("stringField=${'$'}stringField)")
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testCopy() {
        val expected = """
            public fun copy(block: Builder.() -> Unit = {}): TestEndpointParameters {
                return Builder().apply {
                    booleanField = this@TestEndpointParameters.booleanField
                    defaultedBooleanField = this@TestEndpointParameters.defaultedBooleanField
                    defaultedStringField = this@TestEndpointParameters.defaultedStringField
                    @Suppress("DEPRECATION")
                    deprecatedField = this@TestEndpointParameters.deprecatedField
                    @Suppress("DEPRECATION")
                    deprecatedMessageField = this@TestEndpointParameters.deprecatedMessageField
                    @Suppress("DEPRECATION")
                    documentedDeprecatedField = this@TestEndpointParameters.documentedDeprecatedField
                    documentedField = this@TestEndpointParameters.documentedField
                    requiredBooleanField = this@TestEndpointParameters.requiredBooleanField
                    requiredStringField = this@TestEndpointParameters.requiredStringField
                    stringField = this@TestEndpointParameters.stringField
                    block()
                }
                .build()
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testBuilder() {
        val expected = """
            public class Builder {
                public var booleanField: Boolean? = null
         
                public var defaultedBooleanField: Boolean? = true
         
                public var defaultedStringField: String? = "default_string"
         
                @Deprecated("This field is deprecated and no longer recommended for use.")
                public var deprecatedField: String? = null
         
                @Deprecated("arbitrary message about deprecation, has \"a quoted phrase\"")
                public var deprecatedMessageField: String? = null
         
                /**
                 * description about a field but it's deprecated
                 */
                @Deprecated("it's deprecated")
                public var documentedDeprecatedField: String? = null
         
                /**
                 * A documented field. Has "a quoted phrase".
                 */
                public var documentedField: String? = null
         
                public var requiredBooleanField: Boolean? = null
         
                public var requiredStringField: String? = null
         
                public var stringField: String? = null
         
                public fun build(): TestEndpointParameters = TestEndpointParameters(this)
            }
        """.formatForTest()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }
}
