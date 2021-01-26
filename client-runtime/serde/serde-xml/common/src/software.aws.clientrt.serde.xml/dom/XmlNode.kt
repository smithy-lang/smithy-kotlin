/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml.dom

import software.aws.clientrt.serde.DeserializationException
import software.aws.clientrt.serde.xml.XmlStreamReader
import software.aws.clientrt.serde.xml.XmlToken
import software.aws.clientrt.serde.xml.xmlStreamReader

/**
 * Represents an XML name (local) annotated with a namespace identifier (namespace)
 */
data class XmlName(val local: String, val namespace: String? = null)

/**
 * DOM representation of an XML document
 */
class XmlNode {
    var name: XmlName? = null
    // child element name -> children
    var children: MutableMap<String, MutableList<XmlNode>> = linkedMapOf()
    var text: String? = null
    var attributes: MutableMap<String, String> = linkedMapOf()
    var namespaces: MutableMap<String, String> = linkedMapOf()
    var parent: XmlNode? = null

    override fun toString(): String = "XmlNode($name)"

    companion object {

        suspend fun parse(xmlpayload: ByteArray): XmlNode {
            val reader = xmlStreamReader(xmlpayload)
            return parseDom(reader)
        }

        internal fun fromToken(token: XmlToken.BeginElement): XmlNode {
            return XmlNode().apply {
                name = XmlName(token.id.name, token.id.namespace)
                attributes = token.attributes.mapKeys { it.key.name }.toMutableMap()
                // TODO - namespaces?
            }
        }
    }

    fun addChild(child: XmlNode) {
        val name = requireNotNull(child.name) { "child must have a name" }
        val childNodes = children.getOrPut(name.local) {
            mutableListOf()
        }
        childNodes.add(child)
    }

    internal operator fun XmlNode.unaryPlus() = addChild(this)
}

// parse a string into a dom representation
internal suspend fun parseDom(reader: XmlStreamReader): XmlNode {

    val nodeStack: Stack<XmlNode> = mutableListOf()

    loop@while (true) {
        when (val token = reader.nextToken()) {
            is XmlToken.BeginElement -> {
                val newNode = XmlNode.fromToken(token)
                if (nodeStack.isNotEmpty()) {
                    val curr = nodeStack.peek()
                    curr.addChild(newNode)
                    newNode.parent = curr
                }

                nodeStack.push(newNode)
            }
            is XmlToken.EndElement -> {
                val curr = nodeStack.peek()
                requireNotNull(curr.name) { "node must have a name" }
                val currName = curr.name!!.local
                if (currName != token.name.name) {
                    throw DeserializationException("expected end of element: `$currName`, found: `${token.name.name}`")
                }

                if (nodeStack.count() > 1) {
                    // finished with this child node
                    nodeStack.pop()
                }
            }
            is XmlToken.Text -> {
                val curr = nodeStack.peek()
                curr.text = token.value
            }
            is XmlToken.EndDocument -> break@loop
        }
    }

    // root node should be all that is left
    check(nodeStack.count() == 1) { "invalid XML document, node stack size > 1" }
    return nodeStack.pop()
}

fun <T> MutableList<T>.push(item: T) = add(item)
fun <T> MutableList<T>.pop(): T = removeLast()
fun <T> MutableList<T>.popOrNull(): T? = removeLastOrNull()
fun <T> MutableList<T>.peek(): T = this[count() - 1]
fun <T> MutableList<T>.peekOrNull(): T? = if (isNotEmpty()) peek() else null
typealias Stack<T> = MutableList<T>

// build an XML document
fun xml(block: XmlNode.() -> Unit): XmlNode {
    val root = XmlNode()
    root.block()
    return root
}

fun XmlNode.toXmlString(pretty: Boolean = false): String {
    val sb = StringBuilder()
    formatXmlNode(this, 0, sb, pretty)
    return sb.toString()
}

internal fun formatXmlNode(curr: XmlNode, depth: Int, sb: StringBuilder, pretty: Boolean) {
    sb.apply {
        val indent = if (pretty) " ".repeat(depth * 4) else ""

        // FIXME - handle namespaces
        // open tag
        val name = requireNotNull(curr.name)
        append("$indent<${name.local}")

        // attributes
        if (curr.attributes.isNotEmpty()) append(" ")
        curr.attributes.forEach {
            append("${it.key}=\"${it.value}\"")
        }
        append(">")

        // text
        if (curr.text != null) append(curr.text)

        // children
        if (pretty && curr.children.isNotEmpty()) appendLine()
        curr.children.forEach {
            it.value.forEach { child ->
                formatXmlNode(child, depth + 1, sb, pretty)
            }
        }

        // end tag
        if (curr.children.isNotEmpty()) {
            append(indent)
        }
        append("</${name.local}>")

        if (pretty && depth > 0) appendLine()
    }
}
