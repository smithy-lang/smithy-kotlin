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
 * Indicates the annotated structure is unwrapped XML output.
 *
 * Refer to this [s3 specific example](https://smithy.io/2.0/aws/customizations/s3-customizations.html#aws-customizations-s3unwrappedxmloutput-trait)
 */
class UnwrappedXMLOutput(node: ObjectNode) : AnnotationTrait(ID, node) {
    companion object {
        val ID: ShapeId = ShapeId.from("smithy.kotlin.traits#unwrappedXmlOutput")
    }
    constructor() : this(Node.objectNode())
}
