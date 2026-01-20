/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen

import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.protocols.RpcV2Cbor
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Integration that registers protocol generators this package provides
 */
class ProtocolGeneratorSupplier : KotlinIntegration {
    override val protocolGenerators: List<ProtocolGenerator> =
        listOf(
            RpcV2Cbor(),
        )
}
