/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.model.knowledge

import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.KnowledgeIndex
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.rulesengine.traits.ContextParamTrait
import software.amazon.smithy.rulesengine.traits.OperationContextParamDefinition
import software.amazon.smithy.rulesengine.traits.OperationContextParamsTrait
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait

/**
 * Provides endpoint parameter binding knowledge index
 */
class EndpointParameterIndex private constructor(model: Model) : KnowledgeIndex {
    private val opIndex = OperationIndex.of(model)

    /**
     * Get the static context params for an operation
     *
     * @param op operation shape
     * @return the static context params if they exist
     */
    fun staticContextParams(op: OperationShape): StaticContextParamsTrait? = op.getTrait<StaticContextParamsTrait>()

    /**
     * Get the `inputContextParams` for an operation.
     *
     * @param op the operation shape to get context params for
     * @return map of parameter name to input member shape
     */
    fun inputContextParams(op: OperationShape) =
        // maps endpoint parameter name -> input member shape
        buildMap {
            opIndex.getInput(op).getOrNull()?.members()?.forEach { member ->
                member.getTrait<ContextParamTrait>()?.let { trait ->
                    put(trait.name, member)
                }
            }
        }

    /**
     * Get the [operationContextParams](https://smithy.io/2.0/additional-specs/rules-engine/parameters.html#smithy-rules-operationcontextparams-trait)
     * for an operation.
     *
     * @param op the operation shape to get context params for.
     */
    fun operationContextParams(op: OperationShape): Map<String, OperationContextParamDefinition>? =
        op.getTrait<OperationContextParamsTrait>()?.parameters

    /**
     * Check if there are any context parameters bound to an operation
     *
     * @param op operation to check parameters for
     * @return true if there are any static, input, or operation context parameters for the given operation
     */
    fun hasContextParams(op: OperationShape): Boolean =
        staticContextParams(op) != null || inputContextParams(op).isNotEmpty() || operationContextParams(op) != null

    companion object {
        fun of(model: Model): EndpointParameterIndex = EndpointParameterIndex(model)
    }
}
