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
    public fun serialize(serializer: ShapeSerializer<*>)
}
