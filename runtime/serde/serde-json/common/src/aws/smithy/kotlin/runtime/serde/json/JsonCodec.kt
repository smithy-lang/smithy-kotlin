/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.serde.schema.serde.Codec
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeDeserializer
import aws.smithy.kotlin.runtime.serde.schema.serde.ShapeSerializer

/**
 * The JSON [Codec]
 */
public class JsonCodec(
    override val settings: JsonCodecSettings = JsonCodecSettings(),
) : Codec<ByteArray> {
    override fun createSerializer(): ShapeSerializer<ByteArray> = JsonShapeSerializer(settings)

    override fun createDeserializer(source: ByteArray): ShapeDeserializer = JsonShapeDeserializer(source, settings)

    override fun toString(): String = "JsonCodec($settings)"
}
