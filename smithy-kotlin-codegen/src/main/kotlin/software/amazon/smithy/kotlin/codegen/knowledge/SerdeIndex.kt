/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.knowledge

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.KnowledgeIndex
import software.amazon.smithy.model.neighbor.RelationshipType
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*

/**
 * Optionally provides a reference to the referring shape of a given shape.
 */
data class ReferencedShape(val referringMember: Shape?, val shape: Shape)

/**
 * Knowledge index that provides access to shapes requiring serialize and deserialize implementations.
 */
class SerdeIndex(private val model: Model) : KnowledgeIndex {
    companion object {
        fun of(model: Model): SerdeIndex = SerdeIndex(model)
    }

    /**
     * Find and return the set of shapes that are not operation inputs but do require a serializer
     *
     * Operation inputs get an implementation of `HttpSerialize`, everything else gets an implementation
     * of `SdkSerializable`.
     *
     * @return The set of shapes that require a serializer implementation
     */
    fun requiresDocumentSerializer(operations: List<OperationShape>): Set<ReferencedShape> {
        // all top level operation inputs get an HttpSerialize
        // any structure or union shape that shows up as a nested member (direct or indirect)
        // as well as collections of the same requires a serializer implementation though
        val topLevelMembers = operations
            .filter { it.input.isPresent }
            .flatMap {
                val inputShape = model.expectShape(it.input.get())
                inputShape.members()
            }
            .map {  ReferencedShape(it, model.expectShape(it.target)) }
            .filter { it.shape.isStructureShape || it.shape.isUnionShape || it.shape is CollectionShape || it.shape.isMapShape }
            .toSet()

        return walkNestedShapesRequiringSerde(model, topLevelMembers)
    }

    /**
     * Find and return the set of shapes that are not operation outputs but do require a deserializer
     *
     * Operation outputs get an implementation of `HttpDeserialize`, everything else gets a `deserialize()`
     * implementation
     *
     * @return The set of shapes that require a deserializer implementation
     */
    fun requiresDocumentDeserializer(operations: List<OperationShape>): Set<Shape> {
        // All top level operation outputs and errors get an HttpDeserialize implementation.
        // Any structure or union shape that shows up as a nested member, direct or indirect (of either the operation
        // or it's errors), as well as collections of the same requires a deserializer implementation though.

        // add shapes reachable from the operational output itself
        val topLevelMembers = operations
            .filter { it.output.isPresent }
            .flatMap {
                val outputShape = model.expectShape(it.output.get())
                outputShape.members()
            }
            .map { model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .map { ReferencedShape(null, it) }
            .toMutableSet()

        // add shapes reachable from operational errors
        val modeledErrors = operations
            .flatMap { it.errors }
            .flatMap { model.expectShape(it).members() }
            .map { model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .map { ReferencedShape(null, it) }
            .toSet()

        topLevelMembers += modeledErrors

        return walkNestedShapesRequiringSerde(model, topLevelMembers).map { it.shape }.toSet()
    }
}

private fun walkNestedShapesRequiringSerde(model: Model, referencedShapes: Set<ReferencedShape>): Set<ReferencedShape> {
    val resolved = mutableSetOf<ReferencedShape>()
    val walker = Walker(model)

    // walk all the shapes in the set and find all other
    // structs/unions (or collections thereof) in the graph from that shape
    referencedShapes.forEach { shapeArc ->
        walker.iterateShapes(shapeArc.shape) { relationship ->
            when (relationship.relationshipType) {
                RelationshipType.MEMBER_TARGET,
                RelationshipType.STRUCTURE_MEMBER,
                RelationshipType.LIST_MEMBER,
                RelationshipType.SET_MEMBER,
                RelationshipType.MAP_VALUE,
                RelationshipType.UNION_MEMBER -> true
                else -> false
            }
        }.forEach { walkedShape ->
            if (walkedShape.type == ShapeType.STRUCTURE || walkedShape.type == ShapeType.UNION) {
                if (!resolved.any { it.shape == walkedShape }) resolved.add(resolveParentForShape(walkedShape, shapeArc))
            }
        }
    }
    return resolved
}

fun resolveParentForShape(walkedShape: Shape, referencedShape: ReferencedShape): ReferencedShape = when (referencedShape.shape) {
    is CollectionShape -> ReferencedShape(referencedShape.shape.member, walkedShape)
    is MapShape -> ReferencedShape(referencedShape.shape, walkedShape)
    is UnionShape -> {
        val parent = referencedShape.shape.members().find { it.target == walkedShape.id }
        ReferencedShape(parent, walkedShape)
    }
    else -> ReferencedShape(referencedShape.referringMember, walkedShape)
}
