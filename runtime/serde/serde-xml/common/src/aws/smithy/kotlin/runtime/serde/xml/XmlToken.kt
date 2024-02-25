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
        override fun toString(): String = when (prefix) {
            null -> local
            else -> "$prefix:$local"
        }

        @InternalApi
        public companion object {

            /**
             * Construct a [QualifiedName] from a raw string representation
             */
            public fun from(qualified: String): QualifiedName {
                val split = qualified.split(":", limit = 2)
                val (local, prefix) = when (split.size == 2) {
                    true -> split[1] to split[0]
                    false -> split[0] to null
                }
                return QualifiedName(local, prefix)
            }
        }
    }

    /**
     * The opening of an XML element
     */
    @InternalApi
    public data class BeginElement(
        override val depth: Int,
        public val qualifiedName: QualifiedName,
        public val attributes: Map<QualifiedName, String> = emptyMap(),
        public val nsDeclarations: List<Namespace> = emptyList(),
    ) : XmlToken() {

        // Convenience constructor for name-only nodes.
        public constructor(depth: Int, name: String) : this(depth, QualifiedName(name))

        // Convenience constructor for name-only nodes with attributes.
        public constructor(depth: Int, name: String, attributes: Map<QualifiedName, String>) : this(depth, QualifiedName(name), attributes)

        override fun toString(): String = "<${this.qualifiedName} (${this.depth})>"

        // convenience function for codegen
        public fun getAttr(qualified: String): String? = attributes[QualifiedName.from(qualified)]

        /**
         * Get the qualified tag name of this element
         */
        val name: String
            get() = qualifiedName.toString()
    }

    /**
     * The closing of an XML element
     */
    @InternalApi
    public data class EndElement(override val depth: Int, public val qualifiedName: QualifiedName) : XmlToken() {
        // Convenience constructor for name-only nodes.
        public constructor(depth: Int, name: String) : this(depth, QualifiedName(name))

        override fun toString(): String = "</${this.qualifiedName}> (${this.depth})"

        /**
         * Get the qualified tag name of this element
         */
        val name: String
            get() = qualifiedName.toString()
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
        is BeginElement -> "<${this.qualifiedName}>"
        is EndElement -> "</${this.qualifiedName}>"
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
    if (qualifiedName != beginToken.qualifiedName) return false

    return true
}
