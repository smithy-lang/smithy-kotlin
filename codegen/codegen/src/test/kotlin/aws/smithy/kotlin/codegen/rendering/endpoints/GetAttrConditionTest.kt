/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints

import aws.smithy.kotlin.codegen.test.assertBalancedBracesAndParens
import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.newWriter
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import kotlin.test.*

class GetAttrConditionTest {

    @Test
    fun testGetAttrBooleanStandaloneCondition() {
        val rules = createTestRuleSet(
            """
            {
                "version": "1.1",
                "parameters": {
                    "Endpoint": {
                        "type": "string",
                        "required": true,
                        "documentation": "The endpoint URL"
                    }
                },
                "rules": [
                    {
                        "documentation": "getAttr boolean as standalone condition",
                        "type": "tree",
                        "conditions": [
                            {
                                "fn": "parseURL",
                                "argv": [{"ref": "Endpoint"}],
                                "assign": "url"
                            },
                            {
                                "fn": "getAttr",
                                "argv": [{"ref": "url"}, "isIp"]
                            }
                        ],
                        "rules": [
                            {
                                "documentation": "IP-based endpoint",
                                "type": "endpoint",
                                "conditions": [],
                                "endpoint": {
                                    "url": "https://ip-based.example.com"
                                }
                            }
                        ]
                    },
                    {
                        "documentation": "fallback",
                        "type": "endpoint",
                        "conditions": [],
                        "endpoint": {
                            "url": "https://default.example.com"
                        }
                    }
                ]
            }
            """,
        )

        val generated = renderRules(rules)
        generated.assertBalancedBracesAndParens()

        val expected = """
            if (
                url != null &&
                url?.isIp == true
            ) {
        """.formatForTest(indent = "            ")
        generated.shouldContainOnlyOnceWithDiff(expected)
        println(generated)
    }

    @Test
    fun testGetAttrBooleanWithBooleanEquals() {
        val rules = createTestRuleSet(
            """
            {
                "version": "1.1",
                "parameters": {
                    "Endpoint": {
                        "type": "string",
                        "required": true,
                        "documentation": "The endpoint URL"
                    }
                },
                "rules": [
                    {
                        "documentation": "getAttr boolean wrapped in booleanEquals",
                        "type": "tree",
                        "conditions": [
                            {
                                "fn": "parseURL",
                                "argv": [{"ref": "Endpoint"}],
                                "assign": "url"
                            },
                            {
                                "fn": "booleanEquals",
                                "argv": [
                                    {
                                        "fn": "getAttr",
                                        "argv": [{"ref": "url"}, "isIp"]
                                    },
                                    true
                                ]
                            }
                        ],
                        "rules": [
                            {
                                "documentation": "IP-based endpoint",
                                "type": "endpoint",
                                "conditions": [],
                                "endpoint": {
                                    "url": "https://ip-based.example.com"
                                }
                            }
                        ]
                    },
                    {
                        "documentation": "fallback",
                        "type": "endpoint",
                        "conditions": [],
                        "endpoint": {
                            "url": "https://default.example.com"
                        }
                    }
                ]
            }
            """,
        )

        val generated = renderRules(rules)
        generated.assertBalancedBracesAndParens()

        val expected = """
            if (
                url != null &&
                url?.isIp == true
            ) {
        """.formatForTest(indent = "            ")
        generated.shouldContainOnlyOnceWithDiff(expected)
        println(generated)
    }

    @Test
    fun testGetAttrStringWithAssign() {
        val rules = createTestRuleSet(
            """
            {
                "version": "1.1",
                "parameters": {
                    "Endpoint": {
                        "type": "string",
                        "required": true,
                        "documentation": "The endpoint URL"
                    }
                },
                "rules": [
                    {
                        "documentation": "getAttr string with assign",
                        "type": "tree",
                        "conditions": [
                            {
                                "fn": "parseURL",
                                "argv": [{"ref": "Endpoint"}],
                                "assign": "url"
                            },
                            {
                                "fn": "getAttr",
                                "argv": [{"ref": "url"}, "scheme"],
                                "assign": "scheme"
                            }
                        ],
                        "rules": [
                            {
                                "documentation": "use scheme",
                                "type": "endpoint",
                                "conditions": [],
                                "endpoint": {
                                    "url": "https://{scheme}.example.com"
                                }
                            }
                        ]
                    },
                    {
                        "documentation": "fallback",
                        "type": "error",
                        "conditions": [],
                        "error": "Could not parse URL"
                    }
                ]
            }
            """,
        )

        val generated = renderRules(rules)
        generated.assertBalancedBracesAndParens()

        val expected = """
            val url = parseUrl(params.endpoint)
            val scheme = url?.scheme
            if (
                url != null &&
                scheme != null
            ) {
        """.formatForTest(indent = "            ")
        generated.shouldContainOnlyOnceWithDiff(expected)
        println(generated)
    }

    @Test
    fun testGetAttrBooleanMixedConditions() {
        val rules = createTestRuleSet(
            """
            {
                "version": "1.1",
                "parameters": {
                    "Endpoint": {
                        "type": "string",
                        "required": true,
                        "documentation": "The endpoint URL"
                    },
                    "UseFIPS": {
                        "type": "boolean",
                        "required": true,
                        "documentation": "Use FIPS",
                        "default": false
                    }
                },
                "rules": [
                    {
                        "documentation": "mixed conditions with getAttr boolean",
                        "type": "tree",
                        "conditions": [
                            {
                                "fn": "parseURL",
                                "argv": [{"ref": "Endpoint"}],
                                "assign": "url"
                            },
                            {
                                "fn": "booleanEquals",
                                "argv": [{"ref": "UseFIPS"}, true]
                            },
                            {
                                "fn": "getAttr",
                                "argv": [{"ref": "url"}, "isIp"]
                            }
                        ],
                        "rules": [
                            {
                                "documentation": "FIPS IP endpoint",
                                "type": "endpoint",
                                "conditions": [],
                                "endpoint": {
                                    "url": "https://fips-ip.example.com"
                                }
                            }
                        ]
                    },
                    {
                        "documentation": "fallback",
                        "type": "endpoint",
                        "conditions": [],
                        "endpoint": {
                            "url": "https://default.example.com"
                        }
                    }
                ]
            }
            """,
        )

        val generated = renderRules(rules)
        generated.assertBalancedBracesAndParens()

        val expected = """
            if (
                url != null &&
                params.useFips == true &&
                url?.isIp == true
            ) {
        """.formatForTest(indent = "            ")
        generated.shouldContainOnlyOnceWithDiff(expected)
        println(generated)
    }

    private fun renderRules(rules: EndpointRuleSet): String {
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
        return writer.toString()
    }

    private fun createTestRuleSet(json: String): EndpointRuleSet = EndpointRuleSet.fromNode(Node.parse(json))
}
