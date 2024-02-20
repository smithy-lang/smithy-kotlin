/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols.xml

import software.amazon.smithy.kotlin.codegen.protocols.SerdeProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.*
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.XmlParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.XmlSerializerGenerator
import software.amazon.smithy.model.shapes.ShapeId

/**
 * Protocol generator for testing [SerdeXmlProtocol].
 */
object SerdeXmlProtocolGenerator : SerdeProtocolGenerator() {
    override val contentTypes = ProtocolContentTypes.consistent("application/xml")
    override val protocol: ShapeId = SerdeXmlProtocol.ID

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
        XmlParserGenerator(this, defaultTimestampFormat)

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
        XmlSerializerGenerator(this, defaultTimestampFormat)
}
