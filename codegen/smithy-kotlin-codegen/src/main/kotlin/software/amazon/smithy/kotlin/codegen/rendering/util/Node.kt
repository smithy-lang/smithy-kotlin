package software.amazon.smithy.kotlin.codegen.rendering.util

import software.amazon.smithy.kotlin.codegen.utils.dq
import software.amazon.smithy.model.node.ArrayNode
import software.amazon.smithy.model.node.BooleanNode
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.NullNode
import software.amazon.smithy.model.node.NumberNode
import software.amazon.smithy.model.node.ObjectNode
import software.amazon.smithy.model.node.StringNode

/**
 * Renders a [Node] into String format for codegen.
 */
fun Node.render(): String = when (this) {
    is NullNode -> "null"
    is StringNode -> value.dq()
    is BooleanNode -> value.toString()
    is NumberNode -> value.toString()
    is ArrayNode -> elements.joinToString(",", "listOf(", ")") { element ->
        element.render()
    }
    is ObjectNode -> buildString {
        append("mapOf(")
        stringMap.forEach { (key, value) ->
            append("\t${key.dq()} to ${value.render()}")
        }
        append(")")
    }
    else -> throw Exception("Unexpected node type: $this")
}
