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
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.rulesengine.traits.ContextParamTrait
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait


/**
 * Provides endpoint parameter binding knowledge index
 */
class EndpointParameterIndex private constructor(model: Model): KnowledgeIndex {
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
    fun inputContextParams(op: OperationShape): Map<String, MemberShape> {
        // maps endpoint parameter name -> input member shape
        return buildMap {
            opIndex.getInput(op).getOrNull()?.members()?.forEach { member ->
                member.getTrait<ContextParamTrait>()?.let { trait ->
                    put(trait.name, member)
                }
            }
        }
    }

    /**
     * Check if there are any context parameters bound to an operation
     *
     * @param op operation to check parameters for
     * @return true if there are any static or input context parameters for the given operation
     */
    fun hasContextParams(op: OperationShape): Boolean =
        staticContextParams(op) != null || inputContextParams(op).isNotEmpty()

    companion object {
        fun of(model: Model): EndpointParameterIndex = EndpointParameterIndex(model)
    }

}