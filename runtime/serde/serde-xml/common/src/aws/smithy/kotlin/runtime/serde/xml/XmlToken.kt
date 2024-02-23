/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Raw tokens produced when reading an XML document as a stream
 */
@InternalApi
public sealed class XmlToken {

    public abstract val depth: Int

    /**
     * An namespace declaration (xmlns)
     */
    @InternalApi
    public data class Namespace(public val uri: String, public val prefix: String? = null)

    /**
     * Defines the name and namespace of an element
     * @property local The localized name of an element
     * @property prefix The namespace this element belongs to
     */
    @InternalApi
    public data class QualifiedName(public val local: String, public val prefix: String? = null) {
        override fun toString(): String = tag

        public val tag: String get() = when (prefix) {
            null -> local
            else -> "$prefix:$local"
        }
    }

    /**
     * The opening of an XML element
     */
    @InternalApi
    public data class BeginElement(
        override val depth: Int,
        public val name: QualifiedName,
        public val attributes: Map<QualifiedName, String> = emptyMap(),
        public val nsDeclarations: List<Namespace> = emptyList(),
    ) : XmlToken() {
        // Convenience constructor for name-only nodes.
        public constructor(depth: Int, name: String) : this(depth, QualifiedName(name))

        // Convenience constructor for name-only nodes with attributes.
        public constructor(depth: Int, name: String, attributes: Map<QualifiedName, String>) : this(depth, QualifiedName(name), attributes)

        override fun toString(): String = "<${this.name} (${this.depth})>"

        // convenience function for codegen
        public fun getAttr(qualified: String): String? = attributes[QualifiedName(qualified)]
    }

    /**
     * The closing of an XML element
     */
    @InternalApi
    public data class EndElement(override val depth: Int, public val name: QualifiedName) : XmlToken() {
        // Convenience constructor for name-only nodes.
        public constructor(depth: Int, name: String) : this(depth, QualifiedName(name))

        override fun toString(): String = "</${this.name}> (${this.depth})"
    }

    /**
     * An XML element text as string
     */
    @InternalApi
    public data class Text(override val depth: Int, public val value: String?) : XmlToken() {
        override fun toString(): String = "${this.value} (${this.depth})"
    }

    @InternalApi
    public object StartDocument : XmlToken() {
        override val depth: Int = 0
    }

    /**
     * The end of the XML stream to signal that the XML-encoded value has no more
     * tokens
     */
    @InternalApi
    public object EndDocument : XmlToken() {
        override val depth: Int
            get() = 0
    }

    override fun toString(): String = when (this) {
        is BeginElement -> "<${this.name}>"
        is EndElement -> "</${this.name}>"
        is Text -> "${this.value}"
        StartDocument -> "[StartDocument]"
        EndDocument -> "[EndDocument]"
    }
}

// Determine if a given token signals end of (sub/full) document
internal fun XmlToken?.isTerminal(minimumDepth: Int = 0) = when (this) {
    null, XmlToken.EndDocument -> true
    else -> depth < minimumDepth
}

internal fun XmlToken?.isNotTerminal(minimumDepth: Int = 0) = !this.isTerminal(minimumDepth)

// Return true if the passed in node is the beginning node, false otherwise.
internal fun XmlToken?.terminates(beginToken: XmlToken?): Boolean {
    if (this == null || beginToken == null) return false
    if (this !is XmlToken.EndElement) return false
    if (beginToken !is XmlToken.BeginElement) return false
    if (depth != beginToken.depth) return false
    if (name != beginToken.name) return false

    return true
}
