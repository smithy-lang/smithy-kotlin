/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.model

import aws.smithy.kotlin.codegen.core.KotlinSymbolProvider
import aws.smithy.kotlin.codegen.core.defaultName
import aws.smithy.kotlin.codegen.model.traits.OperationInput
import aws.smithy.kotlin.codegen.model.traits.OperationOutput
import aws.smithy.kotlin.codegen.model.traits.SYNTHETIC_NAMESPACE
import aws.smithy.kotlin.codegen.model.traits.SyntheticClone
import aws.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.neighbor.NeighborProvider
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TraitDefinition
import software.amazon.smithy.model.traits.UnitTypeTrait
import software.amazon.smithy.model.transform.ModelTransformer

/**
 * Generate synthetic input and output shapes for a operations as needed and normalize the names.
 *
 * The normalization process leaves the cloned shape(s) in the model. Nothing is generated for these
 * though if they aren't in the service shape's closure anymore (assuming you are only walking shapes for said
 * closure which [aws.smithy.kotlin.codegen.CodegenVisitor] does). Other references to a cloned shape whose name
 * matches its clone's are redirected to the clone so that it doesn't remain in the closure.
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

        val serviceShape = model.expectShape<ServiceShape>(service)
        val builder = model.toBuilder()
        val clones = mutableListOf<StructureShape>()
        operations.forEach { operation ->
            val newInputShape: StructureShape = operation.input
                .map { cloneOperationIOShape(operation.id, model.expectShape<StructureShape>(it), REQUEST_SUFFIX) }
                .orElseGet { emptyOperationIOStruct(operation.id, REQUEST_SUFFIX) }

            val newOutputShape: StructureShape = operation.output
                .map { cloneOperationIOShape(operation.id, model.expectShape<StructureShape>(it), RESPONSE_SUFFIX) }
                .orElseGet { emptyOperationIOStruct(operation.id, RESPONSE_SUFFIX) }

            clones += listOf(newInputShape, newOutputShape)
            builder.addShapes(newInputShape, newOutputShape)
            // update model operation with the input/output shapes
            builder.addShape(
                operation.toBuilder()
                    .input(newInputShape)
                    .output(newOutputShape)
                    .build(),
            )
        }

        val normalized = redirectSameNameReferences(builder.build(), serviceShape, clones)
        validateNoSymbolConflicts(normalized, serviceShape, clones)
        return normalized
    }

    /**
     * An operation input/output shape may also be referenced elsewhere (e.g. as a member of another structure). If
     * the original shape and its clone resolve to the same name, keeping both in the service closure generates two
     * identical types in the same file. Repoint such references at the clone so only one shape remains.
     *
     * References to originals whose name differs from their clone(s) are left alone: the original still generates
     * its own distinct type, and an original shared by several operations has no single clone to choose.
     */
    private fun redirectSameNameReferences(model: Model, service: ServiceShape, clones: List<StructureShape>): Model {
        val redirects: Map<ShapeId, ShapeId> = clones
            .mapNotNull { clone ->
                val archetype = clone.expectTrait<SyntheticClone>().archetype
                model.getShape(archetype).getOrNull()
                    ?.takeIf { it.defaultName(service) == clone.defaultName(service) }
                    ?.let { archetype to clone.id }
            }
            .toMap()
        if (redirects.isEmpty()) return model

        val updatedMembers = Walker(model).walkShapes(service)
            .filterIsInstance<MemberShape>()
            // leave the (now unreferenced) originals themselves untouched
            .filter { it.target in redirects && it.container !in redirects }
            .map { it.toBuilder().target(redirects.getValue(it.target)).build() }
        if (updatedMembers.isEmpty()) return model

        // replacing members also rebuilds their containing shapes
        return ModelTransformer.create().replaceShapes(model, updatedMembers)
    }

    /**
     * Verify that no shape remaining in the service closure resolves to the same name as a synthetic clone, i.e.
     * that [redirectSameNameReferences] left no dangling references to an original shape whose type would collide
     * with its clone's.
     */
    private fun validateNoSymbolConflicts(model: Model, service: ServiceShape, clones: List<StructureShape>) {
        val cloneIds = clones.map { it.id }.toSet()
        val clonesByName = clones.associateBy { it.defaultName(service) }
        val closure = Walker(model).walkShapes(service)
        val closureIds = closure.map { it.id }.toSet()

        val conflicts = closure
            .filter {
                // trait definitions (which are also structures) don't generate types
                it.id !in cloneIds && !it.hasTrait<TraitDefinition>() && KotlinSymbolProvider.isTypeGeneratedForShape(it)
            }
            .mapNotNull { shape -> clonesByName[shape.defaultName(service)]?.let { shape to it } }
        if (conflicts.isEmpty()) return

        val reverseNeighbors = NeighborProvider.reverse(model)
        val formatted = conflicts.joinToString(separator = "\n") { (shape, clone) ->
            val referrers = reverseNeighbors.getNeighbors(shape)
                .map { rel -> rel.shape.id.withoutMember() }
                .filter { it in closureIds && it != shape.id }
                .distinct()
                .sorted()
                .joinToString()
            " * ${shape.id} (conflicts with ${clone.id}; referenced by: $referrers)"
        }
        throw CodegenException(
            """normalizing operation inputs or outputs left shapes in the service closure which conflict with a synthetic input or output:
            |$formatted
            |Fix by supplying a manual rename customization for the shapes listed.
            """.trimMargin(),
        )
    }

    private fun validateTransform(model: Model, service: ShapeId, operations: Set<OperationShape>) {
        // list of all renamed shapes
        val newNames = operations.flatMap {
            listOf(it.id.name + REQUEST_SUFFIX, it.id.name + RESPONSE_SUFFIX)
        }.toSet()

        val shapes = Walker(model).iterateShapes(model.expectShape(service))
        val shapesResultingInType = shapes.asSequence().filter {
            // remove trait definitions (which are also structures)
            !it.hasTrait<TraitDefinition>() && KotlinSymbolProvider.isTypeGeneratedForShape(it)
        }.toList()

        val possibleConflicts = shapesResultingInType.filter { it.id.name in newNames }
        if (possibleConflicts.isEmpty()) return

        val operationInputIds = operations.mapNotNull { it.input.getOrNull() }.toSet()
        val operationOutputIds = operations.mapNotNull { it.output.getOrNull() }.toSet()
        val allIds = operationInputIds + operationOutputIds

        // a type that has the same name as a rename is a possible candidate for conflict
        // we have to check if the type is an operational input or not. If it's an operational
        // input already then a rename is effectively a no-op (any other references to it are redirected to the
        // clone by redirectSameNameReferences), if it isn't then it's going to conflict
        val realConflicts = possibleConflicts.filterNot { it.id in allIds }
        if (realConflicts.isNotEmpty()) {
            val formatted = realConflicts.joinToString(separator = "\n", prefix = " * ") { it.id.toString() }
            throw CodegenException(
                """renaming operation inputs or outputs will result in a conflict for:
                |$formatted
                |Fix by supplying a manual rename customization for the shapes listed.
                """.trimMargin(),
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

    private fun emptyOperationIOStruct(opShapeId: ShapeId, suffix: String): StructureShape = StructureShape
        .builder()
        .id(syntheticShapeId(opShapeId, suffix))
        .addTrait(SyntheticClone.build { archetype = UnitTypeTrait.UNIT })
        .addTrait(if (suffix == REQUEST_SUFFIX) OperationInput() else OperationOutput())
        .build()

    private fun cloneOperationIOShape(opShapeId: ShapeId, structure: StructureShape, suffix: String): StructureShape = structure
        .toBuilder()
        .id(syntheticShapeId(opShapeId, suffix))
        .addTrait(SyntheticClone.build { archetype = structure.id })
        .addTrait(if (suffix == REQUEST_SUFFIX) OperationInput() else OperationOutput())
        .build()
}
