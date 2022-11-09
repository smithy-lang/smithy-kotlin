/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParameterBindingGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.ResolveEndpointMiddlewareGenerator
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.rulesengine.language.EndpointRuleSet
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait

/**
 * Default endpoint resolver middleware
 */
class ResolveEndpointMiddleware : ProtocolMiddleware {
    override val name: String = "ResolveEndpoint"

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val rules = EndpointRuleSet.fromNode(ctx.service.expectTrait<EndpointRuleSetTrait>().ruleSet)
        val middlewareSymbol = ResolveEndpointMiddlewareGenerator.getSymbol(ctx.settings)
        writer.withBlock("op.install(#T(config.endpointProvider)) {", "}", middlewareSymbol) {
            EndpointParameterBindingGenerator(ctx.model, ctx.service, writer, op, rules, "input.").render()
        }
    }
}
