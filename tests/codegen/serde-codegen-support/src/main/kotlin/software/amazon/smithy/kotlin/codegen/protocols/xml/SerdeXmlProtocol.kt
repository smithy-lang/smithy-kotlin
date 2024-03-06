/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols.xml

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Dummy protocol for use in testing projects that need to test XML codegen. Generates XML-based serializers/deserializers.
 */
class SerdeXmlProtocol : AnnotationTrait {
    companion object {
        val ID: ShapeId = ShapeId.from("aws.serde.protocols#serdeXml")
        class Provider : AnnotationTrait.Provider<SerdeXmlProtocol>(ID, ::SerdeXmlProtocol)
    }

    constructor(node: ObjectNode) : super(ID, node)
    constructor() : this(Node.objectNode())
}
