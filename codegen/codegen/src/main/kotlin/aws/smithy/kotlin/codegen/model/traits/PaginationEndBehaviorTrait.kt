/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.model.traits

import software.amazon.smithy.model.FromSourceLocation
import software.amazon.smithy.model.SourceLocation
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.StringTrait

class PaginationEndBehaviorTrait(
    val value: PaginationEndBehavior,
    sourceLocation: FromSourceLocation,
) : StringTrait(ID, value.toString(), sourceLocation) {
    companion object {
        val ID = ShapeId.from("smithy.kotlin.traits#paginationEndBehavior")
    }

    constructor(value: PaginationEndBehavior = PaginationEndBehavior.Default) : this(value, SourceLocation.NONE)

    constructor(stringValue: String, sourceLocation: FromSourceLocation) :
        this(PaginationEndBehavior.fromString(stringValue), sourceLocation)

    class Provider : StringTrait.Provider<PaginationEndBehaviorTrait>(ID, ::PaginationEndBehaviorTrait)
}

sealed interface PaginationEndBehavior {
    data object OutputTokenEmpty : PaginationEndBehavior
    data object IdenticalToken : PaginationEndBehavior
    data class TruncationMember(val memberName: String) : PaginationEndBehavior

    companion object {
        val Default = OutputTokenEmpty

        fun fromString(stringValue: String): PaginationEndBehavior {
            val tokens = stringValue.split(":")
            return when (tokens[0]) {
                "OutputTokenEmpty" -> OutputTokenEmpty
                "IdenticalToken" -> IdenticalToken
                "TruncationMember" -> TruncationMember(tokens[1])
                else -> error("""Unknown PaginationEndBehavior type "${tokens[0]}"""")
            }
        }
    }
}
