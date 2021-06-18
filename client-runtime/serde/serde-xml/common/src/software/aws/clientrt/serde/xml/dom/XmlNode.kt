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
 * DOM representation of an XML document
 */
class XmlNode {
    val name: XmlToken.QualifiedName

    // child element name (local) -> children
    val children: MutableMap<String, MutableList<XmlNode>> = linkedMapOf()
    var text: String? = null
    val attributes: MutableMap<XmlToken.QualifiedName, String> = linkedMapOf()
    // namespaces declared by this node
    val namespaces: MutableList<XmlToken.Namespace> = mutableListOf()
    var parent: XmlNode? = null

    constructor(name: String) : this(XmlToken.QualifiedName(name))
    constructor(name: XmlToken.QualifiedName) {
        this.name = name
    }

    override fun toString(): String = "XmlNode($name)"

    companion object {

        suspend fun parse(xmlpayload: ByteArray): XmlNode {
            val reader = xmlStreamReader(xmlpayload)
            return parseDom(reader)
        }

        internal fun fromToken(token: XmlToken.BeginElement): XmlNode = XmlNode(token.name).apply {
            attributes.putAll(token.attributes)
            namespaces.addAll(token.nsDeclarations)
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
suspend fun parseDom(reader: XmlStreamReader): XmlNode {

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

                if (curr.name != token.name) {
                    throw DeserializationException("expected end of element: `${curr.name}`, found: `${token.name}`")
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
            null,
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

fun XmlNode.toXmlString(pretty: Boolean = false): String {
    val sb = StringBuilder()
    formatXmlNode(this, 0, sb, pretty)
    return sb.toString()
}

internal fun formatXmlNode(curr: XmlNode, depth: Int, sb: StringBuilder, pretty: Boolean) {
    sb.apply {
        val indent = if (pretty) " ".repeat(depth * 4) else ""

        // open tag
        append("$indent<")
        append(curr.name.tag)
        curr.namespaces.forEach {
            // namespaces declared by this node
            append(" xmlns")
            if (it.prefix != null) {
                append(":${it.prefix}")
            }
            append("=\"${it.uri}\"")
        }

        // attributes
        if (curr.attributes.isNotEmpty()) append(" ")
        curr.attributes.forEach {
            append("${it.key.tag}=\"${it.value}\"")
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

        append("</")
        append(curr.name.tag)
        append(">")

        if (pretty && depth > 0) appendLine()
    }
}
