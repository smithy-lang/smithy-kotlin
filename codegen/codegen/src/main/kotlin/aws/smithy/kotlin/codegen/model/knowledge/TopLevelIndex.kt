/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.model.knowledge

import aws.smithy.kotlin.codegen.utils.getOrNull
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.ServiceShape

class TopLevelIndex(model: Model, service: ServiceShape) {
    private val operations = TopDownIndex(model).getContainedOperations(service)
    private val inputStructs = operations.mapNotNull { it.input.getOrNull() }.map { model.expectShape(it) }
    private val inputMembers = inputStructs.flatMap { it.members() }.toSet()

    fun isTopLevelInputMember(member: MemberShape): Boolean = member in inputMembers
}
