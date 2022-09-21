/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.useFileWriter
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointTestsTrait
import java.io.File

/**
 * Delegator class that manages the rendering of all endpoint resolution source code.
 */
// TODO: pull endpoint traits from service shape once they start appearing in synced models
@Suppress("UNUSED_PARAMETER")
class EndpointsCodegenDelegator(
    private val ctx: CodegenContext,
    private val writers: KotlinDelegator,
    private val service: ServiceShape,
) {
    private val partitionsSymbol = PartitionsGenerator.getSymbol(ctx)

    private val paramsSymbol = EndpointParametersGenerator.getSymbol(ctx)

    private val providerSymbol = EndpointProviderGenerator.getSymbol(ctx)

    private val defaultProviderSymbol = DefaultEndpointProviderGenerator.getSymbol(ctx)

    private val testSymbol = DefaultEndpointProviderTestGenerator.getSymbol(ctx)

    fun render() {
        val serviceName = ctx.settings.pkg.name.split(".").last()
        val rules = EndpointRuleSet.fromNode(
            Node.parse(
                File("codegen/sdk/endpoints2-rules/$serviceName/endpoint-rule-set.json").readText(),
            ),
        )
        val testCases = EndpointTestsTrait.fromNode(
            Node.parse(
                File("codegen/sdk/endpoints2-rules/$serviceName/endpoint-tests.json").readText(),
            ),
        )

        val partitionsData =
            javaClass.classLoader.getResource("software/amazon/smithy/kotlin/codegen/partitions.json")?.readText()
                ?: throw CodegenException("could not load partitions.json resource")
        val partitions = Node.parse(partitionsData).expectObjectNode()

        writers.useFileWriter(partitionsSymbol) {
            PartitionsGenerator(it, partitions).render()
        }
        writers.useFileWriter(paramsSymbol) {
            EndpointParametersGenerator(it, rules).render()
        }
        writers.useFileWriter(providerSymbol) {
            EndpointProviderGenerator(it, paramsSymbol).render()
        }
        writers.useFileWriter(defaultProviderSymbol) {
            DefaultEndpointProviderGenerator(it, rules, providerSymbol, paramsSymbol).render()
        }
        writers.useTestFileWriter("${testSymbol.name}.kt", testSymbol.namespace) {
            DefaultEndpointProviderTestGenerator(it, rules, testCases.testCases, defaultProviderSymbol, paramsSymbol).render()
        }
    }
}
