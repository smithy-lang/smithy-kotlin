/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

import java.io.ByteArrayOutputStream
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer

class XmlPullSerializer(pretty: Boolean, private val serializer: XmlSerializer = xmlSerializerFactory()) :
    XmlStreamWriter {

    // Content is serialized to this buffer.
    private val buffer = ByteArrayOutputStream()

    init {
        serializer.setOutput(buffer, null)
        if (pretty) {
            serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", " ".repeat(4))
            serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-line-separator", "\n")
        } else {
            serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", null)
            serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-line-separator", null)
        }
    }

    companion object {
        private fun xmlSerializerFactory(): XmlSerializer {
            val factory = XmlPullParserFactory.newInstance(
                "org.xmlpull.mxp1_serializer.MXSerializer", null
            )
            return factory.newSerializer()
        }
    }

    override fun startDocument(encoding: String?, standalone: Boolean?) {
        serializer.startDocument(encoding, standalone)
    }

    override fun endDocument() {
        serializer.endDocument()
    }

    override fun startTag(name: String, namespace: String?): XmlStreamWriter {
        serializer.startTag(namespace, name)
        return this
    }

    override fun attribute(name: String, value: String?, namespace: String?): XmlStreamWriter {
        serializer.attribute(namespace, name, value)
        return this
    }

    override fun endTag(name: String, namespace: String?): XmlStreamWriter {
        serializer.endTag(namespace, name)
        return this
    }

    override fun text(text: String): XmlStreamWriter {
        serializer.text(text)
        return this
    }

    override fun toString(): String {
        return String(bytes)
    }

    override val bytes: ByteArray
        get() {
            serializer.endDocument()
            serializer.flush()
            return buffer.toByteArray()
        }
}

internal actual fun xmlStreamWriter(pretty: Boolean): XmlStreamWriter = XmlPullSerializer(pretty)
