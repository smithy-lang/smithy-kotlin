/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.withBlock
import software.amazon.smithy.kotlin.codegen.model.getEndpointRules
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.EndpointParameterBindingGenerator
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.ResolveEndpointMiddlewareGenerator
import software.amazon.smithy.model.shapes.OperationShape

/**
 * Default endpoint resolver middleware
 */
class ResolveEndpointMiddleware : ProtocolMiddleware {
    override val name: String = "ResolveEndpoint"

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        val inputSymbol = ctx.symbolProvider.toSymbol(ctx.model.expectShape(op.inputShape))
        writer.withBlock(
            "op.interceptors.add(#T<#T>(config.endpointProvider) {",
            "})",
            ResolveEndpointMiddlewareGenerator.getSymbol(ctx.settings),
            inputSymbol,
        ) {
            ctx.service.getEndpointRules()?.let { rules ->
                EndpointParameterBindingGenerator(ctx.model, ctx.service, writer, op, rules, "it.").render()
            }
        }
    }
}
