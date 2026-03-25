/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.Shape
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.codecs.Decoder
import aws.smithy.kotlin.runtime.serde.codecs.Encoder

public sealed interface Schema : Shape {
    public val shapeType: ShapeType
}

public interface SerializableSchema<T> : Schema {
    public fun deserialize(decoder: Decoder): T
    public fun serialize(encoder: Encoder, value: T)
}
