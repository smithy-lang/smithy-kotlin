/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.protocol

import aws.smithy.kotlin.codegen.KotlinSettings
import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.RequiresLengthTrait
import software.amazon.smithy.model.traits.StreamingTrait
import kotlin.jvm.optionals.getOrNull

/**
 * Adds the [RequiresLengthInterceptor] to operations whose input contains a streaming member
 * targeting a blob with the `@requiresLength` trait.
 */
class RequiresLengthIntegration : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean = model
        .isTraitApplied(RequiresLengthTrait::class.java)

    override fun customizeMiddleware(
        ctx: ProtocolGenerator.GenerationContext,
        resolved: List<ProtocolMiddleware>,
    ): List<ProtocolMiddleware> = resolved + RequiresLengthMiddleware
}

private object RequiresLengthMiddleware : ProtocolMiddleware {
    override val name: String = "RequiresLengthMiddleware"

    override fun isEnabledFor(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Boolean {
        val input = op.input.orElse(null) ?: return false
        val inputShape = ctx.model.expectShape(input, StructureShape::class.java)
        return inputShape.members().any { member ->
            val target = ctx.model.expectShape(member.target)
            target.hasTrait<StreamingTrait>() && target.hasTrait<RequiresLengthTrait>()
        }
    }

    override fun render(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, writer: KotlinWriter) {
        writer.write("op.interceptors.add(#T())", RuntimeTypes.HttpClient.Interceptors.RequiresLengthInterceptor)
    }
}
