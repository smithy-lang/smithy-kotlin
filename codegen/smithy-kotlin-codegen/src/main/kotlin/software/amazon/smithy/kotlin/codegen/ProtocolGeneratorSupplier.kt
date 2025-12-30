/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.protocols.RpcV2Cbor
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Integration that registers protocol generators this package provides
 */
class ProtocolGeneratorSupplier : KotlinIntegration {
    override val order: Byte = -10

    override val protocolGenerators: List<ProtocolGenerator> =
        listOf(
            RpcV2Cbor(),
        )
}
