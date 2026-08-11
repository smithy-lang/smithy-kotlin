/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema.serde

/**
 * A data object that can serialize itself against any [ShapeSerializer] by walking its members. Generated
 * data classes implement this against their static schema.
 */
public interface SerializableStruct {
    /**
     * Serialize this value as a top-level struct: opens the struct with this shape's schema and writes its
     * members.
     */
    public fun serialize(serializer: ShapeSerializer<*>)

    /**
     * Write this value's members into an already-open [dest] struct. Called by
     * [ValueSerializer.writeStruct] when this value is a nested struct/union member, so the enclosing
     * serializer controls the struct framing (and keys it by the member schema).
     */
    public fun serializeMembers(dest: StructSerializer)
}
