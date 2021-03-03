/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.kotlin.codegen.expectShape
import software.amazon.smithy.kotlin.codegen.shapes
import software.amazon.smithy.kotlin.codegen.traits.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*

/**
 * Generate synthetic input and output shapes for a operations as needed and normalize the names.
 *
 * The normalization process leaves the cloned shape(s) in the model. Nothing is generated for these
 * though if they aren't in the service shape's closure anymore (assuming you are only walking shapes for said
 * closure which [software.amazon.smithy.kotlin.codegen.CodegenVisitor] does).
 */
object OperationNormalizer {
    private const val REQUEST_SUFFIX: String = "Request"
    private const val RESPONSE_SUFFIX: String = "Response"

    /**
     * Add synthetic input & output shapes to every Operation in the model. The generated shapes will be marked
     * with [SyntheticClone] trait. If an operation does not have an input or output an empty one will be added.
     * Existing inputs/outputs will be modified to have uniform names.
     */
    fun transform(model: Model): Model {
        validateTransform(model)
        val operations = model.shapes<OperationShape>()
        val builder = model.toBuilder()
        operations.forEach { operation ->
            val newInputShape: StructureShape = operation.input
                .map { cloneOperationIOShape(operation.id, model.expectShape<StructureShape>(it), REQUEST_SUFFIX) }
                .orElseGet { emptyOperationIOStruct(operation.id, REQUEST_SUFFIX) }

            val newOutputShape: StructureShape = operation.output
                .map { cloneOperationIOShape(operation.id, model.expectShape<StructureShape>(it), RESPONSE_SUFFIX) }
                .orElseGet { emptyOperationIOStruct(operation.id, RESPONSE_SUFFIX) }

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

    private fun validateTransform(model: Model) {
        // FIXME - implement some KnowledgeIndex's to commonize the logic for walking a model
    }

    private fun emptyOperationIOStruct(opShapeId: ShapeId, suffix: String): StructureShape {
        return StructureShape.builder()
            .id(ShapeId.fromParts(SYNTHETIC_NAMESPACE, opShapeId.name + suffix))
            .addTrait(SyntheticClone.build { archetype = opShapeId })
            .addTrait(if (suffix == REQUEST_SUFFIX) OperationInput() else OperationOutput())
            .build()
    }

    private fun cloneOperationIOShape(opShapeId: ShapeId, structure: StructureShape, suffix: String): StructureShape {
        // rename operation inputs/outputs by using the operation name
        val cloneShapeId = ShapeId.fromParts(SYNTHETIC_NAMESPACE, opShapeId.name + suffix)
        return structure.toBuilder()
            .id(cloneShapeId)
            .addTrait(SyntheticClone.build { archetype = structure.id })
            .addTrait(if (suffix == REQUEST_SUFFIX) OperationInput() else OperationOutput())
            .build()
    }
}
