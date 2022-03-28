/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.serialization

import aws.smithy.kotlin.runtime.serde.SerializationException
import aws.smithy.kotlin.runtime.serde.xml.XmlStreamWriter
import aws.smithy.kotlin.runtime.serde.xml.XmlToken.QualifiedName

/**
 * An object that buffers XML tags into an output string (via [toString]) or byte array (via [bytes]).
 */
internal class BufferingXmlStreamWriter(val pretty: Boolean = false) : XmlStreamWriter {
    private val buffer = StringBuilder()
    private val nsAttributes = mutableMapOf<QualifiedName, String>()
    private val tagWriterStack = ArrayDeque<LazyTagWriter>()

    /**
     * Gets the byte serialization for this writer. Note that this will call [endDocument] first, closing all open tags.
     */
    override val bytes: ByteArray
        get() = text.encodeToByteArray()

    /**
     * Gets the text serialization for this writer. Note that this will call [endDocument] first, closing all open tags.
     */
    override val text: String
        get() {
            endDocument()
            return buffer.toString()
        }

    /**
     * Adds an attribute to the current tag in this writer.
     * @param name The local name of the attribute
     * @param value The value of the attribute
     * @param namespace The namespace of this attribute (or `null` for no namespace)
     */
    override fun attribute(name: String, value: String?, namespace: String?): XmlStreamWriter {
        val qName = QualifiedName(name, namespace)
        requireWriter().attribute(qName, value)
        return this
    }

    /**
     * Ends this document by closing all open tags.
     */
    override fun endDocument() {
        var top = tagWriterStack.lastOrNull()
        while (top != null) {
            endTag(top.qName)
            top = tagWriterStack.lastOrNull()
        }
    }

    /**
     * Closes the most-recently opened tag. If the given [name] and [namespace] do not match the most-recently opened
     * tag an exception is thrown.
     * @param name The local name of the tag to close.
     * @param namespace The namespace of the tag to close (or `null` for no namespace).
     */
    override fun endTag(name: String, namespace: String?): XmlStreamWriter = endTag(QualifiedName(name, namespace))

    private fun endTag(qName: QualifiedName): XmlStreamWriter {
        val childWriter = tagWriterStack.removeLastOrNull()
            ?: throw SerializationException("Unexpected end of tag while no tags are open")

        if (childWriter.qName != qName) {
            throw SerializationException("Tried to end tag $qName but expected end of ${childWriter.qName} tag")
        }

        if (tagWriterStack.isEmpty()) childWriter.write(buffer)
        return this
    }

    /**
     * Declares a namespace prefix for an upcoming tag (i.e., this is called before the [startTag] method 🤷‍).
     * @param uri The URI for the namespace.
     * @param prefix The name of the namespace which will form a prefix for tag/attribute names which utilize it.
     */
    override fun namespacePrefix(uri: String, prefix: String?) {
        val attrQName = if (prefix == null) QualifiedName("xmlns") else QualifiedName(prefix, "xmlns")
        nsAttributes[attrQName] = uri
    }

    private fun requireWriter(): LazyTagWriter {
        if (tagWriterStack.isEmpty()) {
            throw SerializationException("Attempted to serialize text or attribute without containing tag")
        }
        return tagWriterStack.last()
    }

    /**
     * Starts this XML document by writing the XML declaration (e.g., `<?xml version="1.0"?>`).
     */
    override fun startDocument() {
        buffer.append("""<?xml version="1.0"?>""")
        if (pretty) buffer.appendLine()
    }

    /**
     * Starts a child tag. Note that this accumulates outstanding namespace declarations into attributes for the tag.
     * @param name The local name of the tag.
     * @param namespace The namespace of the tag (or `null` for no namespace).
     */
    override fun startTag(name: String, namespace: String?): XmlStreamWriter {
        val currentWriter = tagWriterStack.lastOrNull()
        val currentIndent = currentWriter?.indentLevel ?: -1

        val qName = QualifiedName(name, namespace)
        val childWriter = LazyTagWriter(pretty, currentIndent + 1, qName, nsAttributes)
        nsAttributes.clear()

        currentWriter?.childTag(childWriter)
        tagWriterStack.addLast(childWriter)

        return this
    }

    /**
     * Adds a child text node to the most-recently opened tag.
     */
    override fun text(text: String): XmlStreamWriter {
        requireWriter().text(text)
        return this
    }
}
