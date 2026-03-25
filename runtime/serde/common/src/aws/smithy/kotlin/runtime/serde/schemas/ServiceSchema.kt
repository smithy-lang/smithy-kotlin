/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait

public interface ServiceSchema : Schema {
    public val operations: List<OperationSchema<*, *>>
    public val errors: List<StructureSchema<*>>
}

private data class ServiceSchemaImpl(
    override val shapeId: ShapeId,
    override val operations: List<OperationSchema<*, *>>,
    override val errors: List<StructureSchema<*>>,
    override val traits: List<Trait>,
) : ServiceSchema {
    override val shapeType: ShapeType = ShapeType.SERVICE
}

public fun ServiceSchema(
    shapeId: ShapeId,
    operations: List<OperationSchema<*, *>>,
    errors: List<StructureSchema<*>>,
    traits: List<Trait>,
): ServiceSchema = ServiceSchemaImpl(shapeId, operations, errors, traits)
