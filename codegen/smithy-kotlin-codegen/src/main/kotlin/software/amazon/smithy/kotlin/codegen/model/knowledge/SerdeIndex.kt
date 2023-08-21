/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.model.knowledge

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.KnowledgeIndex
import software.amazon.smithy.model.neighbor.RelationshipType
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*

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
    fun requiresDocumentSerializer(operations: List<OperationShape>): Set<Shape> {
        // all top level operation inputs get an HttpSerialize
        // any structure or union shape that shows up as a nested member (direct or indirect)
        // as well as collections of the same requires a serializer implementation though
        val topLevelMembers = operations
            .filter { it.input.isPresent }
            .flatMap {
                val inputShape = model.expectShape(it.input.get())
                inputShape.members()
            }
            .map { model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        return walkNestedShapesRequiringSerde(model, topLevelMembers)
    }

    /**
     * Find and return the set of shapes reachable from the given shape that would require a "document" serializer.
     * @return The set of shapes that require a serializer implementation
     */
    fun requiresDocumentSerializer(shape: Shape, members: Collection<MemberShape> = shape.members()): Set<Shape> =
        when (shape) {
            is OperationShape -> requiresDocumentSerializer(listOf(shape))
            else -> {
                val topLevelMembers = members
                    .map { model.expectShape(it.target) }
                    .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
                    .toSet()
                walkNestedShapesRequiringSerde(model, topLevelMembers)
            }
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
            .toMutableSet()

        // add shapes reachable from operational errors
        val modeledErrors = operations
            .flatMap { it.errors }
            .flatMap { model.expectShape(it).members() }
            .map { model.expectShape(it.target) }
            .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
            .toSet()

        topLevelMembers += modeledErrors

        return walkNestedShapesRequiringSerde(model, topLevelMembers)
    }

    /**
     * Find and return the set of shapes reachable from the given shape that would require a "document" deserializer.
     * @return The set of shapes that require a deserializer implementation
     */
    fun requiresDocumentDeserializer(shape: Shape, members: Collection<MemberShape> = shape.members()): Set<Shape> =
        when (shape) {
            is OperationShape -> requiresDocumentDeserializer(listOf(shape))
            else -> {
                val topLevelMembers = members
                    .map { model.expectShape(it.target) }
                    .filter { it.isStructureShape || it.isUnionShape || it is CollectionShape || it.isMapShape }
                    .toMutableSet()
                walkNestedShapesRequiringSerde(model, topLevelMembers)
            }
        }
}

private fun walkNestedShapesRequiringSerde(model: Model, shapes: Set<Shape>): Set<Shape> {
    val resolved = mutableSetOf<Shape>()
    val walker = Walker(model)

    // walk all the shapes in the set and find all other
    // structs/unions (or collections thereof) in the graph from that shape
    shapes.forEach { shape ->
        walker.iterateShapes(shape) { relationship ->
            when (relationship.relationshipType) {
                RelationshipType.MEMBER_TARGET,
                RelationshipType.STRUCTURE_MEMBER,
                RelationshipType.LIST_MEMBER,
                RelationshipType.MAP_VALUE,
                RelationshipType.UNION_MEMBER,
                -> true
                else -> false
            }
        }.forEach { walkedShape ->
            if (walkedShape.type == ShapeType.STRUCTURE || walkedShape.type == ShapeType.UNION) {
                resolved.add(walkedShape)
            }
        }
    }
    return resolved
}
