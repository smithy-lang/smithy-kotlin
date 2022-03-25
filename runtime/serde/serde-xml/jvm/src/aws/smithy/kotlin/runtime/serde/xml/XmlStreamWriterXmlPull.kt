/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.serde.xml

import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class XmlPullSerializer(pretty: Boolean, private val serializer: XmlSerializer = xmlSerializerFactory()) :
    XmlStreamWriter {

    // Content is serialized to this buffer.
    private val buffer = ByteArrayOutputStream()

    init {
        serializer.setOutput(buffer, StandardCharsets.UTF_8.name())
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
        text.forEach { character ->
            when (character) {
                '\n' -> serializer.entityRef("#xA")
                '\r' -> serializer.entityRef("#xD")
                '\u0085' -> serializer.entityRef("#x85")
                '\u2028' -> serializer.entityRef("#x2028")
                else -> serializer.text(character.toString())
            }
        }
        return this
    }

    override fun namespacePrefix(uri: String, prefix: String?) {
        serializer.setPrefix(prefix ?: "", uri)
    }

    override fun toString(): String = String(bytes)

    override val bytes: ByteArray
        get() {
            serializer.endDocument()
            serializer.flush()
            return buffer.toByteArray()
        }
}

actual fun xmlPullStreamWriter(pretty: Boolean): XmlStreamWriter = XmlPullSerializer(pretty)
