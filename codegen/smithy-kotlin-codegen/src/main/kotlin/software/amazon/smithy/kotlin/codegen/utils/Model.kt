package software.amazon.smithy.kotlin.codegen.utils

import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.utils.SmithyInternalApi

/**
 * Syntactic sugar for getting a services operations
 */
@SmithyInternalApi
fun Model.operations(service: ShapeId): Set<OperationShape> {
    val topDownIndex = TopDownIndex.of(this)
    return topDownIndex.getContainedOperations(service)
}
