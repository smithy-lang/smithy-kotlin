/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.protocols.json

import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Dummy protocol for use in serde-benchmark project models. Generates JSON based serializers/deserializers
 */
class SerdeBenchmarkJsonProtocol : KotlinIntegration {
    companion object {
        val ID: ShapeId = ShapeId.from("aws.benchmarks.protocols#serdeBenchmarkJson")
    }

    override val protocolGenerators: List<ProtocolGenerator> = listOf(SerdeBenchmarkJsonProtocolGenerator)
}
