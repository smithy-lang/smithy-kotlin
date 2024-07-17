/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import kotlin.test.*

// this is a simplified ruleset test to verify that the core constructs are generated properly
// the services ship with test cases that are far more comprehensive which actually test the BEHAVIOR of this generated code
class DefaultEndpointProviderGeneratorTest {
    private val generatedClass: String

    init {
        val rules = createTestRuleSet(
            """
                {
                    "version": "1.1",
                    "parameters": {
                        "ResourceId": {
                            "type": "string",
                            "required": true
                        },
                        "BazName": {
                            "type": "string",
                            "required": false
                        },
                        "QuxName": {
                            "type": "string",
                            "required": false
                        },
                        "accountId": {
                            "type": "string",
                            "required": false
                        },
                        "endpoint": {
                            "type": "string",
                            "required": false
                        }
                    },
                    "rules": [
                        {
                            "documentation": "basic condition",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "BazName"}
                                    ]
                                },
                                {
                                    "fn": "stringEquals",
                                    "argv": [
                                        {"ref": "BazName"},
                                        "gov"
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://basic.condition"
                            }
                        },
                        {
                            "documentation": "assignment condition",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "substring",
                                    "argv": [ {"ref": "ResourceId"}, 0, 4, false ],
                                    "assign": "resourceIdPrefix"
                                },
                                {
                                    "fn": "stringEquals",
                                    "argv": [
                                        {"ref": "resourceIdPrefix"},
                                        "gov.{ResourceId}"
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://assignment.condition"
                            }
                        },
                        {
                            "documentation": "throw exception if bad value",
                            "type": "error",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "BazName"}
                                    ]
                                },
                                {
                                    "fn": "stringEquals",
                                    "argv": [
                                        {"ref": "BazName"},
                                        "invalid"
                                    ]
                                }
                            ],
                            "error": "invalid BazName value"
                        },
                        {
                            "documentation": "fallback to global endpoint",
                            "type": "endpoint",
                            "conditions": [],
                            "endpoint": {
                                "url": "https://global.api",
                                "properties": {
                                    "foo": "bar",
                                    "fooInt": 7,
                                    "fooBoolean": true,
                                    "fooObject": {
                                        "fooObjectFoo": "bar"
                                    },
                                    "fooArray": [
                                        "\"fooArrayBar\""
                                    ]
                                },
                                "headers": {
                                    "fooheader": ["barheader"]
                                }
                            }
                        },
                        {
                            "documentation": "account id based endpoint and service endpoint override",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "accountId"}
                                    ]
                                },
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "endpoint"}
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://{accountId}.{endpoint}"
                            }
                        },
                        {
                            "documentation": "service endpoint override",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "endpoint"}
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://{endpoint}"
                            }
                        },
                        {
                            "documentation": "account id based endpoint",
                            "type": "endpoint",
                            "conditions": [
                                {
                                    "fn": "isSet",
                                    "argv": [
                                        {"ref": "accountId"}
                                    ]
                                }
                            ],
                            "endpoint": {
                                "url": "https://{accountId}"
                            }
                        }
                    ]
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

        DefaultEndpointProviderGenerator(testCtx.generationCtx, rules, writer).render()
        generatedClass = writer.toString()
    }

    private fun createTestRuleSet(json: String): EndpointRuleSet =
        EndpointRuleSet.fromNode(Node.parse(json))

    @Test
    fun testBalancedSyntax() {
        generatedClass.assertBalancedBracesAndParens()
    }

    @Test
    fun testBasicCondition() {
        val expected = """
            if (
                params.bazName != null &&
                params.bazName == "gov"
            ) {
                return Endpoint(
                    Url.parse("https://basic.condition"),
                )
            }
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testAssignmentCondition() {
        val expected = """
            run {
                val resourceIdPrefix = substring(params.resourceId, 0, 4, false)
                if (
                    resourceIdPrefix != null &&
                    resourceIdPrefix == "gov.${'$'}{params.resourceId}"
                ) {
                    return Endpoint(
                        Url.parse("https://assignment.condition"),
                    )
                }
            }
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testException() {
        val expected = """
            if (
                params.bazName != null &&
                params.bazName == "invalid"
            ) {
                throw EndpointProviderException("invalid BazName value")
            }
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testEndpointFields() {
        val expected = """
            return Endpoint(
                Url.parse("https://global.api"),
                headers = Headers {
                    append("fooheader", "barheader")
                },
                attributes = attributesOf {
                    "foo" to "bar"
                    "fooInt" to 7
                    "fooBoolean" to true
                    "fooObject" to buildDocument {
                        "fooObjectFoo" to "bar"
                    }
                    "fooArray" to listOf(
                        "\"fooArrayBar\"",
                    )
                },
            )
        """.formatForTest(indent = "        ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testBusinessMetrics() {
        val moneySign = "$"

        val accountIdAndEndpoint = """
            return Endpoint(
                Url.parse("https://$moneySign{params.accountId}.$moneySign{params.endpoint}"),
                attributes = attributesOf {
                    AccountIdBasedEndpointAccountId to params.accountId
                    EndpointOverride to true
                },
            )
        """

        val accountId = """
            return Endpoint(
                Url.parse("https://$moneySign{params.accountId}"),
                attributes = attributesOf {
                    AccountIdBasedEndpointAccountId to params.accountId
                },
            )
        """

        val endpoint = """
            return Endpoint(
                Url.parse("https://$moneySign{params.endpoint}"),
                attributes = attributesOf {
                    EndpointOverride to true
                },
            )
        """

        generatedClass.shouldContainOnlyOnceWithDiff(accountIdAndEndpoint)
        generatedClass.shouldContainOnlyOnceWithDiff(accountId)
        generatedClass.shouldContainOnlyOnceWithDiff(endpoint)
    }
}
