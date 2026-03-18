/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.json

import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.serde.Codec
import aws.smithy.kotlin.runtime.serde.Deserializer
import aws.smithy.kotlin.runtime.serde.Serializer
import aws.smithy.kotlin.runtime.serde.SmithyTimestampFormat

public class JsonCodec private constructor(builder: Builder) : Codec {
    public companion object {
        public operator fun invoke(builder: Builder.() -> Unit): JsonCodec = JsonCodec(Builder().apply(builder))
    }

    public val defaultTimestampFormat: SmithyTimestampFormat = builder.defaultTimestampFormat
    public val useHttpBindings: Boolean = builder.useHttpBindings
    public val useJsonName: Boolean = builder.useJsonName

    public class Builder {
        public var defaultTimestampFormat: SmithyTimestampFormat = SmithyTimestampFormat.EPOCH_SECONDS
        public var useHttpBindings: Boolean = true
        public var useJsonName: Boolean = true
    }

    override fun createDeserializer(source: SdkSource): Deserializer {
        TODO("Not yet implemented")
    }

    override fun createSerializer(sink: SdkSink): Serializer {
        TODO("Not yet implemented")
    }
}
