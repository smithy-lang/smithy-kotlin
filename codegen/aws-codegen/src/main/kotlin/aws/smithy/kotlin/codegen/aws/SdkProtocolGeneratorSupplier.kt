/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.aws

import aws.smithy.kotlin.codegen.aws.protocols.AwsJson1_0
import aws.smithy.kotlin.codegen.aws.protocols.AwsJson1_1
import aws.smithy.kotlin.codegen.aws.protocols.AwsQuery
import aws.smithy.kotlin.codegen.aws.protocols.Ec2Query
import aws.smithy.kotlin.codegen.aws.protocols.RestJson1
import aws.smithy.kotlin.codegen.aws.protocols.RestXml
import aws.smithy.kotlin.codegen.aws.protocols.RpcV2Cbor
import aws.smithy.kotlin.codegen.integration.KotlinIntegration
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Integration that registers protocol generators this package provides
 */
class SdkProtocolGeneratorSupplier : KotlinIntegration {
    /**
     * Gets the sort order of the customization from -128 to 127, with lowest
     * executed first.
     *
     * @return Returns the sort order, defaults to -10.
     */
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
