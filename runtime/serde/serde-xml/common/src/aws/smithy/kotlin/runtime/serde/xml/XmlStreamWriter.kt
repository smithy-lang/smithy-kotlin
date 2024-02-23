/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.serde.xml.serialization.BufferingXmlStreamWriter

/**
 * Defines an interface to serialization of an XML Infoset.
 */
@InternalApi
public interface XmlStreamWriter {

    /**
     * Write the XML declaration.
     */
    public fun startDocument()

    /**
     * Finish writing. All unclosed start tags will be closed and output
     * will be flushed.
     */
    public fun endDocument()

    /**
     * Writes a start tag with the given namespace and name.
     * If there is no prefix defined for the given namespace,
     * a prefix will be defined automatically.
     * The explicit prefixes for namespaces can be established by calling [namespacePrefix]
     * immediately before this method.
     * If namespace is null no namespace prefix is printed but just name.
     * If namespace is empty string then serializer will make sure that
     * default empty namespace is declared (in XML 1.0 xmlns='')
     * or throw IllegalStateException if default namespace is already bound
     * to non-empty string.
     */
    @InternalApi
    public fun startTag(name: String, namespace: String? = null): XmlStreamWriter

    /**
     * Write an attribute. Calls to attribute() MUST follow a call to
     * startTag() immediately. If there is no prefix defined for the
     * given namespace, a prefix will be defined automatically.
     * If namespace is null or empty string
     * no namespace prefix is printed but just name.
     */
    public fun attribute(name: String, value: String?, namespace: String? = null): XmlStreamWriter

    /**
     * Write end tag. Repetition of namespace and name is just for avoiding errors.
     */
    @InternalApi
    public fun endTag(name: String, namespace: String? = null): XmlStreamWriter

    /**
     * Writes text, where special XML chars are escaped automatically
     */
    public fun text(text: String): XmlStreamWriter

    /**
     * Set the namespace prefix
     */
    @InternalApi
    public fun namespacePrefix(uri: String, prefix: String? = null)

    /**
     * Gets the byte serialization for this writer. Note that this will call [endDocument] first, closing all open tags.
     */
    public val bytes: ByteArray

    /**
     *
     */
    public val text: String
}

@InternalApi
public fun XmlStreamWriter.data(text: Number) {
    this.text(text.toString())
}

/*
* Creates a [XmlStreamWriter] instance to write XML
*/
@InternalApi
public fun xmlStreamWriter(pretty: Boolean = false): XmlStreamWriter = BufferingXmlStreamWriter(pretty)
