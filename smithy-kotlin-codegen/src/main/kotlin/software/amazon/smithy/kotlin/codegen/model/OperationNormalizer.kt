/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.model

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.kotlin.codegen.traits.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.Trait

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
     *
     * @param model The model to transform
     * @param service The service shape ID used to determine the closure of operations to work on
     */
    fun transform(model: Model, service: ShapeId): Model {
        // smithy implicitly loads all models found on the classpath we have to be careful to only deal with
        // shapes in the closure of the service we care about
        val topDownIndex = TopDownIndex.of(model)
        val operations = topDownIndex.getContainedOperations(service)

        validateTransform(model, service, operations)

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

    private fun validateTransform(model: Model, service: ShapeId, operations: Set<OperationShape>) {
        // list of all renamed shapes
        val newNames = operations.flatMap {
            listOf(it.id.name + REQUEST_SUFFIX, it.id.name + RESPONSE_SUFFIX)
        }.toSet()

        val shapes = Walker(model).iterateShapes(model.expectShape(service))
        val shapesResultingInType = shapes.asSequence().filter {
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

    private fun syntheticShapeId(opShapeId: ShapeId, suffix: String): ShapeId {
        // see ABNF: https://awslabs.github.io/smithy/1.0/spec/core/model.html#shape-id
        // take the last part of the namespace and clone shapes into the synthetic namespace using the trailing
        // part of the original namespace as a suffix. e.g. "com.foo#Bar" -> "smithy.kotlin.synthetic.foo#Bar"
        val lastNs = opShapeId.namespace.split(".").last()
        return ShapeId.fromParts("$SYNTHETIC_NAMESPACE.$lastNs", opShapeId.name + suffix)
    }

    private fun emptyOperationIOStruct(opShapeId: ShapeId, suffix: String): StructureShape {
        return StructureShape.builder()
            .id(syntheticShapeId(opShapeId, suffix))
            .addTrait(SyntheticClone.build { archetype = opShapeId })
            .addTrait(if (suffix == REQUEST_SUFFIX) OperationInput() else OperationOutput())
            .build()
    }

    private fun cloneOperationIOShape(opShapeId: ShapeId, structure: StructureShape, suffix: String): StructureShape {
        // rename operation inputs/outputs by using the operation name
        return structure.toBuilder()
            .id(syntheticShapeId(opShapeId, suffix))
            .addTrait(SyntheticClone.build { archetype = structure.id })
            .addTrait(if (suffix == REQUEST_SUFFIX) OperationInput() else OperationOutput())
            .build()
    }
}
