/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.model.traits

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Indicates the annotated member is a truncation indicator which conveys a non-standard termination condition for
 * pagination.
 */
class PaginationTruncationMember(node: ObjectNode) : AnnotationTrait(ID, node) {
    companion object {
        val ID: ShapeId = ShapeId.from("smithy.kotlin.traits#paginationTruncationMember")
    }

    constructor() : this(Node.objectNode())
}
