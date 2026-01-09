/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols

import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.core.withBlock
import aws.smithy.kotlin.codegen.lang.KotlinTypes
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import aws.smithy.kotlin.codegen.rendering.protocol.HttpProtocolClientGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpTraitResolver
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolContentTypes
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.errorHandler
import aws.smithy.kotlin.codegen.rendering.protocol.errorHandlerName
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

abstract class SerdeProtocolGenerator : HttpBindingProtocolGenerator() {
    abstract val contentTypes: ProtocolContentTypes

    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS

    override fun getHttpProtocolClientGenerator(ctx: ProtocolGenerator.GenerationContext): HttpProtocolClientGenerator =
        object : HttpProtocolClientGenerator(
            ctx,
            listOf(),
            getProtocolHttpBindingResolver(ctx.model, ctx.service),
        ) { }

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) = Unit

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(model, serviceShape, ProtocolContentTypes.consistent("application/json"))

    override fun operationErrorHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol =
        op.errorHandler(ctx.settings) { writer ->
            writer.withBlock(
                "private fun ${op.errorHandlerName()}(context: #T, call: #T, payload: #T?): Nothing {",
                "}",
                RuntimeTypes.Core.ExecutionContext,
                RuntimeTypes.Http.HttpCall,
                KotlinTypes.ByteArray,
            ) {
                write("error(\"not needed for codegen related tests\")")
            }
        }
}
