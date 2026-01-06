/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.util

import aws.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.BooleanNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NullNode
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode

/**
 * Formats a [Node] into a String for codegen.
 */
fun Node.format(): String = when (this) {
    is NullNode -> "null"
    is StringNode -> value.dq()
    is BooleanNode -> value.toString()
    is NumberNode -> value.toString()
    is ArrayNode -> elements.joinToString(",", "listOf(", ")") { element ->
        element.format()
    }
    is ObjectNode -> stringMap.entries.joinToString(", ", "mapOf(", ")") { (key, value) ->
        "${key.dq()} to ${value.format()}"
    }
    else -> throw IllegalStateException("Unexpected node type: $this")
}
