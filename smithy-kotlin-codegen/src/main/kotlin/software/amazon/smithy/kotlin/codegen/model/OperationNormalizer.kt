/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.traits.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.Trait
import kotlin.streams.toList

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
        val operations = model.shapes<OperationShape>()
        // list of all renamed shapes
        val newNames = operations.flatMap {
            listOf(it.id.name + REQUEST_SUFFIX, it.id.name + RESPONSE_SUFFIX)
        }.toSet()

        val shapesResultingInType = model.shapes().filter {
            // remove trait definitions (which are also structures)
            !it.hasTrait<Trait>() && SymbolVisitor.isTypeGenerateForShape(it)
        }.toList()

        val possibleConflicts = shapesResultingInType.filter { it.id.name in newNames }
        if (possibleConflicts.isEmpty()) return

        val operationInputIds = operations.mapNotNull { it.input.getOrNull() }.toSet()
        val operationOutputIds = operations.mapNotNull { it.output.getOrNull() }.toSet()
        val allIds = operationInputIds + operationOutputIds

        // a type that has the same name as a rename is a possible candidate for conflict
        // we have to check if the type is an operational input or not. If it's an operational
        // input already then a rename is effectively a no-op, if it isn't then it's going to conflict
        val realConflicts = possibleConflicts.filterNot { it.id in allIds }
        if (realConflicts.isNotEmpty()) {
            val formatted = realConflicts.joinToString(separator = "\n", prefix = " * ") { it.id.toString() }
            throw CodegenException(
                """renaming operation inputs or outputs will result in a conflict for:
                |$formatted
                |Fix by supplying a manual rename customization for the shapes listed.
            """.trimMargin()
            )
        }
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
