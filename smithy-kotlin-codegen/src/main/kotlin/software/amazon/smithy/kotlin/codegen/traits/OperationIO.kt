/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.traits

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Marker trait that indicates a (Structure) shape is an operation's input
 */
class OperationInput : AnnotationTrait {
    companion object {
        val ID: ShapeId = ShapeId.from("smithy.kotlin.traits#operationInput")
    }

    constructor(node: ObjectNode) : super(ID, node)
    constructor() : this(Node.objectNode())
}

/**
 * Marker trait that indicates a (Structure) shape is an operation's output
 */
class OperationOutput : AnnotationTrait {
    companion object {
        val ID: ShapeId = ShapeId.from("smithy.kotlin.traits#operationOutput")
    }

    constructor(node: ObjectNode) : super(ID, node)
    constructor() : this(Node.objectNode())
}
