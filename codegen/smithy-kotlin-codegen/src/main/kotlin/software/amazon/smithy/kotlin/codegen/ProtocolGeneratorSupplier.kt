/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.protocols.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Integration that registers protocol generators
 */
class ProtocolGeneratorSupplier : KotlinIntegration {
    override val order: Byte = -10
    override val protocolGenerators: List<ProtocolGenerator> =
        listOf(
            AwsJson1_0(),
            AwsJson1_1(),
            RestJson1(),
            RestXml(),
            AwsQuery(),
            Ec2Query(),
            RpcV2Cbor(),
        )
}
