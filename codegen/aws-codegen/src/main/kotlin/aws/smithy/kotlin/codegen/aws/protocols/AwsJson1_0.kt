/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.aws.protocols

import aws.smithy.kotlin.codegen.aws.protocols.json.AwsJsonHttpBindingResolver
import aws.smithy.kotlin.codegen.aws.protocols.json.AwsJsonProtocolMiddleware
import aws.smithy.kotlin.codegen.aws.protocols.json.AwsJsonProtocolParserGenerator
import aws.smithy.kotlin.codegen.aws.protocols.json.JsonHttpBindingProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolMiddleware
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.aws.traits.protocols.AwsJson1_0Trait
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Handles generating the aws.protocols#awsJson1_0 protocol for services.
 *
 * @inheritDoc
 * @see HttpBindingProtocolGenerator
 */
@Suppress("ktlint:standard:class-naming")
class AwsJson1_0 : JsonHttpBindingProtocolGenerator() {
    override val protocol: ShapeId = AwsJson1_0Trait.ID
    override val supportsJsonNameTrait: Boolean = false

    override fun getDefaultHttpMiddleware(ctx: ProtocolGenerator.GenerationContext): List<ProtocolMiddleware> {
        val httpMiddleware = super.getDefaultHttpMiddleware(ctx)
        val awsJsonMiddleware = listOf(
            AwsJsonProtocolMiddleware(ctx.settings.service, "1.0"),
        )

        return httpMiddleware + awsJsonMiddleware
    }

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        AwsJsonHttpBindingResolver(model, serviceShape, "application/x-amz-json-1.0")

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        AwsJsonProtocolParserGenerator(this, supportsJsonNameTrait)
}
