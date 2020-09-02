/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.serde.xml

/**
 * Defines an interface to serialization of an XML Infoset.
 */
interface XmlStreamWriter {

    /**
     * Write xml declaration with encoding (if encoding not null)
     * and standalone flag (if standalone not null)
     * This method can only be called just after setOutput.
     */
    fun startDocument(encoding: String? = null, standalone: Boolean? = null)

    /**
     * Finish writing. All unclosed start tags will be closed and output
     * will be flushed. After calling this method no more output can be
     * serialized until next call to setOutput()
     */
    fun endDocument()

    /**
     * Writes a start tag with the given namespace and name.
     * If there is no prefix defined for the given namespace,
     * a prefix will be defined automatically.
     * The explicit prefixes for namespaces can be established by calling setPrefix()
     * immediately before this method.
     * If namespace is null no namespace prefix is printed but just name.
     * If namespace is empty string then serializer will make sure that
     * default empty namespace is declared (in XML 1.0 xmlns='')
     * or throw IllegalStateException if default namespace is already bound
     * to non-empty string.
     */
    fun startTag(name: String, namespace: String? = null): XmlStreamWriter

    /**
     * Write an attribute. Calls to attribute() MUST follow a call to
     * startTag() immediately. If there is no prefix defined for the
     * given namespace, a prefix will be defined automatically.
     * If namespace is null or empty string
     * no namespace prefix is printed but just name.
     */
    fun attribute(name: String, value: String?, namespace: String? = null): XmlStreamWriter

    /**
     * Write end tag. Repetition of namespace and name is just for avoiding errors.
     */
    fun endTag(name: String, namespace: String? = null): XmlStreamWriter

    /**
     * Writes text, where special XML chars are escaped automatically
     */
    fun text(text: String): XmlStreamWriter

    /**
     * XML content will be constructed in this UTF-8 encoded byte array.
     */
    val bytes: ByteArray
}

fun XmlStreamWriter.text(text: Long) {
    this.text(text.toString())
}

fun XmlStreamWriter.text(text: Int) {
    this.text(text.toString())
}

fun XmlStreamWriter.text(text: Double) {
    this.text(text.toString())
}

fun XmlStreamWriter.text(text: Boolean) {
    this.text(text.toString())
}

fun XmlStreamWriter.text(text: Byte) {
    this.text(text.toString())
}

fun XmlStreamWriter.text(text: Short) {
    this.text(text.toString())
}

fun XmlStreamWriter.text(text: Float) {
    this.text(text.toString())
}

/*
* Creates a [XmlStreamWriter] instance to write XML
*/
internal expect fun xmlStreamWriter(pretty: Boolean = false): XmlStreamWriter
