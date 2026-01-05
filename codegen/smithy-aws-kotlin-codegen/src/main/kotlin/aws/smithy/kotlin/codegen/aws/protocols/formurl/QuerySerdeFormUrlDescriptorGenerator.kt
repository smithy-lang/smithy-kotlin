/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.aws.protocols.formurl

import aws.smithy.kotlin.codegen.core.RenderingContext
import aws.smithy.kotlin.codegen.core.RuntimeTypes
import aws.smithy.kotlin.codegen.model.changeNameSuffix
import aws.smithy.kotlin.codegen.model.hasTrait
import aws.smithy.kotlin.codegen.model.traits.OperationInput
import aws.smithy.kotlin.codegen.rendering.serde.FormUrlSerdeDescriptorGenerator
import aws.smithy.kotlin.codegen.rendering.serde.SdkFieldDescriptorTrait
import aws.smithy.kotlin.codegen.rendering.serde.add
import aws.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.Shape

/**
 * A generalized superclass for descriptor generators that follow the "*query" AWS protocols.
 */
abstract class QuerySerdeFormUrlDescriptorGenerator(
    ctx: RenderingContext<Shape>,
    memberShapes: List<MemberShape>? = null,
) : FormUrlSerdeDescriptorGenerator(ctx, memberShapes) {
    override fun getObjectDescriptorTraits(): List<SdkFieldDescriptorTrait> {
        val traits = super.getObjectDescriptorTraits().toMutableList()

        val objectShape = requireNotNull(ctx.shape)
        if (objectShape.hasTrait<OperationInput>()) {
            // see https://awslabs.github.io/smithy/1.0/spec/aws/aws-query-protocol.html#request-serialization

            // operation inputs are normalized in smithy-kotlin::OperationNormalizer to be "[OperationName]Request"
            val action = objectShape.changeNameSuffix("Request" to "")
            val version = service.version
            traits.add(RuntimeTypes.Serde.SerdeFormUrl.QueryLiteral, "Action".dq(), action.dq())
            traits.add(RuntimeTypes.Serde.SerdeFormUrl.QueryLiteral, "Version".dq(), version.dq())
        }

        return traits
    }
}
