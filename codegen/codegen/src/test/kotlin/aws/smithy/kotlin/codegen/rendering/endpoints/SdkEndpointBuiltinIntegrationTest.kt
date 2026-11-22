/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.newWriter
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import kotlin.test.Test
import kotlin.test.assertEquals

class SdkEndpointBuiltinIntegrationTest {
    @Test
    fun testEndpointOnlyParsedAsUrl() {
        val generated = render(
            createTestRuleSet(
                """
                {
                    "type": "endpoint",
                    "conditions": [
                        {
                            "fn": "isSet",
                            "argv": [{"fn": "parseURL", "argv": [{"ref": "Endpoint"}]}]
                        },
                        {
                            "fn": "parseURL",
                            "argv": [{"ref": "Endpoint"}],
                            "assign": "url"
                        }
                    ],
                    "endpoint": {
                        "url": "{url#scheme}://{url#authority}{url#path}"
                    }
                }
                """,
            ),
        )

        // The client binds the endpoint unchanged, so custom endpoint providers see its query, and marks the parameters
        generated.binding.shouldContainOnlyOnceWithDiff(
            """
                endpoint = config.endpointUrl?.toString()
                boundByClient = true
            """.formatForTest(indent = ""),
        )

        // The mark survives `copy` and isn't part of equality
        generated.parameters.shouldContainOnlyOnceWithDiff("    internal val boundByClient: Boolean = builder.boundByClient")
        generated.parameters.shouldContainOnlyOnceWithDiff("            boundByClient = this@TestEndpointParameters.boundByClient")
        generated.parameters.shouldContainOnlyOnceWithDiff("        internal var boundByClient: Boolean = false")
        generated.parameters.shouldContainOnlyOnceWithDiff(
            """
                public override fun equals(other: Any?): Boolean {
                    if (this === other) return true
                    if (other !is TestEndpointParameters) return false
                    if (this.endpoint != other.endpoint) return false
                    return true
                }
            """.formatForTest(),
        )

        // The default provider evaluates the rules without the query of a client-bound endpoint
        generated.provider.shouldContainOnlyOnceWithDiff(
            """
                public override suspend fun resolveEndpoint(params: TestEndpointParameters): Endpoint {
                    if (params.boundByClient) {
                        val endpointWithoutQuery = withoutQuery(params.endpoint)
                        if (endpointWithoutQuery != params.endpoint) return resolveEndpoint(params.copy { endpoint = endpointWithoutQuery })
                    }
            """.formatForTest(),
        )
    }

    @Test
    fun testEndpointParsedAndUsedAsUrl() = assertUnchanged(
        createTestRuleSet(
            """
            {
                "type": "endpoint",
                "conditions": [
                    {
                        "fn": "parseURL",
                        "argv": [{"ref": "Endpoint"}],
                        "assign": "url"
                    }
                ],
                "endpoint": {
                    "url": "{Endpoint}"
                }
            }
            """,
        ),
    )

    @Test
    fun testEndpointNotParsed() = assertUnchanged(
        createTestRuleSet(
            """
            {
                "type": "endpoint",
                "conditions": [],
                "endpoint": {
                    "url": {"ref": "Endpoint"}
                }
            }
            """,
        ),
    )

    // The rules can use a query, so the endpoint is bound unchanged and the parameters and provider are as without the integration
    private fun assertUnchanged(rules: EndpointRuleSet) {
        val generated = render(rules)
        val baseline = render(rules, integrations = emptyList())

        generated.binding.shouldContainOnlyOnceWithDiff("endpoint = config.endpointUrl?.toString()")
        assertEquals(baseline.parameters, generated.parameters)
        assertEquals(baseline.provider, generated.provider)
    }

    private class Generated(val binding: String, val parameters: String, val provider: String)

    private fun render(
        rules: EndpointRuleSet,
        integrations: List<KotlinIntegration> = listOf(SdkEndpointBuiltinIntegration()),
    ): Generated {
        val model =
            """
            namespace com.test
            service Test {
                version: "1.0.0",
            }
            """.toSmithyModel()

        val testCtx = model.newTestContext(integrations = integrations)
        val ctx = testCtx.generationCtx
        val customizations = integrations.mapNotNull { it.customizeEndpointResolution(ctx) }
        fun render(block: (KotlinWriter) -> Unit): String = testCtx.newWriter().also(block).toString()

        return Generated(
            binding = render { writer -> customizations.forEach { it.renderBindEndpointBuiltins(ctx, rules, writer) } },
            parameters = render { EndpointParametersGenerator(ctx, rules, it).render() },
            provider = render { DefaultEndpointProviderGenerator(ctx, rules, it).render() },
        )
    }

    private fun createTestRuleSet(rule: String): EndpointRuleSet = EndpointRuleSet.fromNode(
        Node.parse(
            """
            {
                "version": "1.0",
                "parameters": {
                    "Endpoint": {
                        "type": "string",
                        "builtIn": "SDK::Endpoint",
                        "required": false,
                        "documentation": "The endpoint URL"
                    }
                },
                "rules": [
                    {
                        "type": "tree",
                        "conditions": [
                            {
                                "fn": "isSet",
                                "argv": [{"ref": "Endpoint"}]
                            }
                        ],
                        "rules": [$rule]
                    }
                ]
            }
            """,
        ),
    )
}
