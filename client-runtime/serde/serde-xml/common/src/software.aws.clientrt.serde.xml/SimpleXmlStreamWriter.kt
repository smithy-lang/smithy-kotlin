/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

/**
 * An zero dependency XML Kotlin common compatible serializer
 */
internal class SimpleXmlStreamWriter(private val pretty: Boolean) : XmlStreamWriter {

    private data class Element(
        val name: String,
        val namespace: String?,
        val parentNamespaces: Map<String, String>,
        val newNamespaces: MutableMap<String, String>,
        var startTagOpen: Boolean = true,
        var containsText: Boolean = false
    ) {
        val namespaces: Map<String, String> get() = parentNamespaces + newNamespaces
    }

    private val stack = mutableListOf<Element>()
    private val builder = StringBuilder()
    private val currentElement get() = stack.lastOrNull()
    private val newNamespaces = mutableMapOf<String, String>()
    private var generatedNamespaceName = 1

    override fun startDocument(encoding: String?, standalone: Boolean?) {
        builder.append("""<?xml version="1.0"""")
        encoding?.let {
            builder.append(""" encoding="$encoding"""")
        }
        standalone?.let {
            builder.append(""" standalone="${if (standalone) "yes" else "no"}"""")
        }
        builder.append("?>")
    }

    override fun endDocument() {}

    override fun setPrefix(prefix: String, namespace: String) {
        newNamespaces[namespace] = prefix
    }

    override fun startTag(name: String, namespace: String?): XmlStreamWriter = apply {
        currentElement?.let { element ->
            if (element.containsText) {
                throw IllegalStateException("Element cannot contain both text and other elements (${element.name})")
            }
            if (element.startTagOpen) {
                writeNamespaces(element)
                builder.append(">")
                element.startTagOpen = false
            }
        }
        builder.append(prettify(newline = stack.isNotEmpty())).append("<")

        val element = Element(name, namespace, currentElement?.namespaces ?: emptyMap(), LinkedHashMap(newNamespaces))
        newNamespaces.clear()
        stack.add(element)
        builder.appendNamespace(element,  namespace).append(name)
    }

    override fun attribute(name: String, value: String?, namespace: String?): XmlStreamWriter = apply {
        currentElement?.let { element ->
            if (!element.startTagOpen) {
                throw IllegalStateException("Cannot add attribute to closed element")
            }

            builder.append(" ").appendNamespace(element, namespace).append(name)
            if (value != null) {
                builder.append("=\"").appendEscaped(value).append("\"")
            }

        } ?: throw IllegalStateException("Cannot add attribute outside of an element")
    }

    override fun endTag(name: String, namespace: String?): XmlStreamWriter = apply {
        val last = stack.removeLastOrNull() ?: throw IllegalStateException("Cannot call endTag() outside an element")
        if (name != last.name || namespace != last.namespace) {
            throw IllegalStateException("Trying to end tag '$name' ${namespace?.let { "with namespace '$it'" } ?: ""} expected tag '${last.name}' ${last.namespace?.let { "with namespace '$it'" } ?: ""}")
        }

        if (last.startTagOpen) {
            writeNamespaces(last)
            builder.append(" />")
        } else {
            if (!last.containsText) {
                builder.append(prettify())
            }
            builder.append("</").appendNamespace(last, namespace).append(name).append(">")
        }
    }

    private fun StringBuilder.appendNamespace(element: Element, namespace: String?): StringBuilder = apply {
        if (!namespace.isNullOrEmpty()) {

            val prefix = when(val pf = element.namespaces[namespace]) {
                is String -> pf
                else -> {
                    val newPrefix = "n${generatedNamespaceName++}"
                    element.newNamespaces[namespace] = newPrefix
                    newPrefix
                }
            }
            builder.append(prefix).append(":")
        }
    }

    private fun writeNamespaces(element: Element) {
        element.newNamespaces.forEach { (namespace, prefix) -> writeNamespace(namespace, prefix) }
    }

    private fun writeNamespace(namespace: String, prefix: String) {
        builder.append(" xmlns")
        if (prefix.isNotBlank()) {
            builder.append(":").append(prefix)
        }
        builder.append("=\"")
        builder.append(namespace)
        builder.append("\"")
    }

    override fun text(text: String): XmlStreamWriter = apply {
        val element = currentElement ?: throw IllegalStateException("Cannot add text without an open element")
        element.containsText = true
        if (element.startTagOpen) {
            writeNamespaces(element)
            builder.append(">")
            element.startTagOpen = false
        }
        builder.appendEscaped(text)
    }

    private fun prettify(newline: Boolean = true) = if (pretty && newline) "\n" + " ".repeat(stack.size * 4) else ""

    override fun toString(): String = builder.toString()

    override val bytes: ByteArray
        get() = toString().encodeToByteArray()


    private companion object {
        val XML_ESCAPED_CHARS = mapOf(
            '&' to "&amp;",
            '<' to "&lt;",
            '>' to "&gt;",
            '"' to "&quot;",
            '\'' to "&apos;"
        )

        private fun Appendable.appendEscaped(text: String): Appendable = apply {
            text.forEach { ch ->
                XML_ESCAPED_CHARS[ch]?.let { escape -> append(escape) } ?: append(ch)
            }
        }
    }

}