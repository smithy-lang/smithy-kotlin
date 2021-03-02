/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.smithy.model

import software.amazon.smithy.kotlin.codegen.smithy.expectShape
import software.amazon.smithy.kotlin.codegen.smithy.shapes
import software.amazon.smithy.kotlin.codegen.smithy.traits.SYNTHETIC_NAMESPACE
import software.amazon.smithy.kotlin.codegen.smithy.traits.SyntheticClone
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*

/**
 * Generate synthetic input and output shapes for a operations as needed and normalize the names.
 */
class OperationNormalizer {

    /**
     * Add synthetic input & output shapes to every Operation in the model. The generated shapes will be marked
     * with [SyntheticClone] trait. If an operation does not have an input or output an empty one will be added.
     * Existing inputs/outputs will be modified to have uniform names.
     */
    fun transform(model: Model): Model {
        val operations = model.shapes<OperationShape>()
        val builder = model.toBuilder()
        operations.forEach { operation ->
            val newInputShape: StructureShape = operation.input
                .map { cloneOperationShape(operation.id, model.expectShape<StructureShape>(it), "Request") }
                .orElseGet { emptyOperationStruct(operation.id, "Request") }

            val newOutputShape: StructureShape = operation.output
                .map { cloneOperationShape(operation.id, model.expectShape<StructureShape>(it), "Response") }
                .orElseGet { emptyOperationStruct(operation.id, "Response") }

            builder.addShapes(newInputShape, newOutputShape)
            // update model operation with the input/output shapes
            builder.addShape(
                operation.toBuilder()
                    .input(newInputShape)
                    .output(newOutputShape)
                    .build()
            )
        }
        return builder.build()
    }

    private fun emptyOperationStruct(opShapeId: ShapeId, suffix: String): StructureShape {
        return StructureShape.builder()
            .id(ShapeId.fromParts(SYNTHETIC_NAMESPACE, opShapeId.name + suffix))
            .addTrait(SyntheticClone.build { archetype = opShapeId })
            .build()
    }

    private fun cloneOperationShape(opShapeId: ShapeId, structure: StructureShape, suffix: String): StructureShape {
        // rename operation inputs/outputs by using the operation name
        val cloneShapeId = ShapeId.fromParts(SYNTHETIC_NAMESPACE, opShapeId.name + suffix)
        return structure.toBuilder()
            .id(cloneShapeId)
            .addTrait(SyntheticClone.build { archetype = structure.id })
            .build()
    }
}
