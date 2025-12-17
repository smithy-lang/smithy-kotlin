/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.aws.protocols.core

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.aws.protocols.eventstream.EventStreamParserGenerator
import software.amazon.smithy.kotlin.codegen.aws.protocols.eventstream.EventStreamSerializerGenerator
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.model.shapes.OperationShape

/**
 * Base class for all AWS HTTP protocol generators
 */
abstract class AwsHttpBindingProtocolGenerator : HttpBindingProtocolGenerator() {

    override fun eventStreamRequestHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol {
        val resolver = getProtocolHttpBindingResolver(ctx.model, ctx.service)
        val contentType = resolver.determineRequestContentType(op) ?: error("event streams must set a content-type")
        val eventStreamSerializerGenerator = EventStreamSerializerGenerator(structuredDataSerializer(ctx), contentType)
        return eventStreamSerializerGenerator.requestHandler(ctx, op)
    }

    override fun eventStreamResponseHandler(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol {
        val eventStreamParserGenerator = EventStreamParserGenerator(ctx, structuredDataParser(ctx))
        return eventStreamParserGenerator.responseHandler(ctx, op)
    }
}
