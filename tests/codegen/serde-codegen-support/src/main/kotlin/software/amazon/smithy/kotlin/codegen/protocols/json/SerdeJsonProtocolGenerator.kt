/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols.json

import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolContentTypes
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.serde.JsonParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.JsonSerializerGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.kotlin.codegen.protocols.SerdeProtocolGenerator
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Protocol generator for benchmark protocol [SerdeJsonProtocol]
 */
object SerdeJsonProtocolGenerator : SerdeProtocolGenerator() {
    override val contentTypes = ProtocolContentTypes.consistent("application/json")
    override val protocol: ShapeId = SerdeJsonProtocol.ID

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        JsonSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        JsonParserGenerator(this)
}
