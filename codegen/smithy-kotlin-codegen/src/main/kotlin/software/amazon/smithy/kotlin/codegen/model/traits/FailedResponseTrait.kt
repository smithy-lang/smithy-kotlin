package software.amazon.smithy.kotlin.codegen.model.traits

import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AnnotationTrait

/**
 * Indicates the annotated service should always return a failed response.
 */
class FailedResponseTrait(node: ObjectNode) : AnnotationTrait(ID, node) {
    companion object {
        val ID: ShapeId = ShapeId.from("com.test#failedResponseTrait")
    }
}
