/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.util

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.kotlin.codegen.integration.HttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * Integration that registers protocol generators this package provides
 */
class CodegenTestIntegration : KotlinIntegration {
    /**
     * Gets the sort order of the customization from -128 to 127, with lowest
     * executed first.
     *
     * @return Returns the sort order, defaults to -10.
     */
    override val order: Byte = -10

    override val protocolGenerators: List<ProtocolGenerator> = listOf(TestProtocolGenerator())
}

/**
 * A partial ProtocolGenerator to generate minimal sdks for tests.
 */
class TestProtocolGenerator(
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS,
    override val defaultContentType: String = "application/json",
    override val protocol: ShapeId = RestJson1Trait.ID
) : HttpBindingProtocolGenerator() {

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {
        // NOP
    }
}