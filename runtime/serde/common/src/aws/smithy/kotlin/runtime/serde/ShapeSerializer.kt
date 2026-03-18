/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

public interface ShapeSerializer {
    public fun write(schema: Schema, value: Any?)
    public fun writeStruct(schema: Schema, value: Any)
}
