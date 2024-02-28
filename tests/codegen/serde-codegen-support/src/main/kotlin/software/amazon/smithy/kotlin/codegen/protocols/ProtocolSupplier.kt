/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols

import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.protocols.json.SerdeJsonProtocolGenerator
import software.amazon.smithy.kotlin.codegen.protocols.xml.SerdeXmlProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

class ProtocolSupplier : KotlinIntegration {
    override val protocolGenerators: List<ProtocolGenerator>
        get() = listOf(SerdeJsonProtocolGenerator, SerdeXmlProtocolGenerator)
}
