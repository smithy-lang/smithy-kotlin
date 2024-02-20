/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.protocols.json

import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Dummy protocol for use in serde-benchmark project models. Generates JSON based serializers/deserializers
 */
class SerdeJsonProtocol : AnnotationTrait {
    companion object {
        val ID: ShapeId = ShapeId.from("aws.serde.protocols#serdeJson")
        class Provider : AnnotationTrait.Provider<SerdeJsonProtocol>(ID, ::SerdeJsonProtocol)
    }
    constructor(node: ObjectNode) : super(ID, node)
    constructor() : this(Node.objectNode())
}
