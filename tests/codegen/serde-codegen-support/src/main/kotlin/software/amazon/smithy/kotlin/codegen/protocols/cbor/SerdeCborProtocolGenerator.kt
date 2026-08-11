/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols.cbor

import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolContentTypes
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import aws.smithy.kotlin.codegen.rendering.serde.CborParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.CborSerializerGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import aws.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.kotlin.codegen.protocols.SerdeProtocolGenerator
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Protocol generator for benchmark protocol [SerdeCborProtocol]
 */
object SerdeCborProtocolGenerator : SerdeProtocolGenerator() {
    override val contentTypes = ProtocolContentTypes.consistent("application/cbor")
    override val protocol: ShapeId = SerdeCborProtocol.ID

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator = CborSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator = CborParserGenerator(this)
}
