/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.serde

/**
 * Builds a value of type [T] from a [ShapeDeserializer]. Placing deserialization on the builder lets a
 * shape be populated from multiple sources (e.g. HTTP bindings and the payload).
 */
public interface ShapeBuilder<T> {
    public fun deserialize(deserializer: ShapeDeserializer)
    public fun build(): T
}
