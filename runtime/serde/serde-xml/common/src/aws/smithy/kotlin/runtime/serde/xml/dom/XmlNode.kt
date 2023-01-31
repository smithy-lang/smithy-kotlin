/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.xml.dom

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.serde.DeserializationException
import aws.smithy.kotlin.runtime.serde.xml.XmlStreamReader
import aws.smithy.kotlin.runtime.serde.xml.XmlToken
import aws.smithy.kotlin.runtime.serde.xml.xmlStreamReader
import aws.smithy.kotlin.runtime.util.*

/**
 * DOM representation of an XML document
 */
@InternalApi
public class XmlNode {
    public val name: XmlToken.QualifiedName

    // child element name (local) -> children
    public val children: MutableMap<String, MutableList<XmlNode>> = linkedMapOf()
    public var text: String? = null
    public val attributes: MutableMap<XmlToken.QualifiedName, String> = linkedMapOf()

    // namespaces declared by this node
    public val namespaces: MutableList<XmlToken.Namespace> = mutableListOf()
    public var parent: XmlNode? = null

    public constructor(name: String) : this(XmlToken.QualifiedName(name))
    public constructor(name: XmlToken.QualifiedName) {
        this.name = name
    }

    override fun toString(): String = "XmlNode($name)"

    public companion object {

        public fun parse(xmlpayload: ByteArray): XmlNode {
            val reader = xmlStreamReader(xmlpayload)
            return parseDom(reader)
        }

        internal fun fromToken(token: XmlToken.BeginElement): XmlNode = XmlNode(token.name).apply {
            attributes.putAll(token.attributes)
            namespaces.addAll(token.nsDeclarations)
        }
    }

    public fun addChild(child: XmlNode) {
        val name = requireNotNull(child.name) { "child must have a name" }
        val childNodes = children.getOrPut(name.local) {
            mutableListOf()
        }
        childNodes.add(child)
    }

    internal operator fun XmlNode.unaryPlus() = addChild(this)
}

// parse a string into a dom representation
public fun parseDom(reader: XmlStreamReader): XmlNode {
    val nodeStack: ListStack<XmlNode> = mutableListOf()

    loop@while (true) {
        when (val token = reader.nextToken()) {
            is XmlToken.BeginElement -> {
                val newNode = XmlNode.fromToken(token)
                if (nodeStack.isNotEmpty()) {
                    val curr = nodeStack.top()
                    curr.addChild(newNode)
                    newNode.parent = curr
                }

                nodeStack.push(newNode)
            }
            is XmlToken.EndElement -> {
                val curr = nodeStack.top()

                if (curr.name != token.name) {
                    throw DeserializationException("expected end of element: `${curr.name}`, found: `${token.name}`")
                }

                if (nodeStack.count() > 1) {
                    // finished with this child node
                    nodeStack.pop()
                }
            }
            is XmlToken.Text -> {
                val curr = nodeStack.top()
                curr.text = token.value
            }
            null,
            is XmlToken.EndDocument,
            -> break@loop
            else -> continue // ignore unknown token types
        }
    }

    // root node should be all that is left
    check(nodeStack.count() == 1) { "invalid XML document, node stack size > 1" }
    return nodeStack.pop()
}

public fun XmlNode.toXmlString(pretty: Boolean = false): String {
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
