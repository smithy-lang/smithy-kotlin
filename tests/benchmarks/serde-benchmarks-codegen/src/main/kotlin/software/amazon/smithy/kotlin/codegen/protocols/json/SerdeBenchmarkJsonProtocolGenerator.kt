/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols.json

import software.amazon.smithy.kotlin.codegen.protocols.BenchmarkProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Protocol generator for benchmark protocol [SerdeBenchmarkJsonProtocol]
 */
object SerdeBenchmarkJsonProtocolGenerator : BenchmarkProtocolGenerator() {
    override val contentTypes = ProtocolContentTypes.consistent("application/json")
    override val protocol: ShapeId = SerdeBenchmarkJsonProtocol.ID

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        JsonSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        JsonParserGenerator(this)
}
