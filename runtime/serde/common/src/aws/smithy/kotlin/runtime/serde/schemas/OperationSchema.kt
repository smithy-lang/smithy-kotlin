/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schemas

import aws.smithy.kotlin.runtime.serde.ShapeId
import aws.smithy.kotlin.runtime.serde.ShapeType
import aws.smithy.kotlin.runtime.serde.Trait

public interface OperationSchema<I, O> : Schema {
    public val input: StructureSchema<I>
    public val output: StructureSchema<O>
}

private data class OperationSchemaImpl<I, O>(
    override val shapeId: ShapeId,
    override val input: StructureSchema<I>,
    override val output: StructureSchema<O>,
    override val traits: List<Trait>,
) : OperationSchema<I, O> {
    override val shapeType: ShapeType = ShapeType.OPERATION
}

public fun <I, O> OperationSchema(
    shapeId: ShapeId,
    input: StructureSchema<I>,
    output: StructureSchema<O>,
    traits: List<Trait>,
): OperationSchema<I, O> = OperationSchemaImpl(shapeId, input, output, traits)
