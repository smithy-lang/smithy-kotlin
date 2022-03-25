/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.deserialization

import aws.smithy.kotlin.runtime.serde.xml.XmlToken

/**
 * Describes the internal state of an [XmlLexer].
 */
internal sealed class LexerState {
    /**
     * The node depth at which the lexer is parsing tokens. Like the concept of depth in [XmlToken], this depth is 1 at
     * the root (but 0 outside the root).
     */
    abstract val depth: Int

    /**
     * The initial state at the beginning of a document before reading any tags, DTD, or prolog.
     */
    object Initial : LexerState() {
        override val depth = 0
    }

    /**
     * The lexer is expecting the root tag next.
     */
    object BeforeRootTag : LexerState() {
        override val depth = 0
    }

    /**
     * Describes the state of being inside a tag.
     */
    sealed class Tag : LexerState() {
        abstract val name: XmlToken.QualifiedName
        abstract val parent: OpenTag?

        /**
         * The lexer is inside a tag. The next close tag should match the name of this tag.
         */
        data class OpenTag(
            override val name: XmlToken.QualifiedName,
            override val parent: OpenTag?,
            val seenChildren: Boolean,
        ) : Tag() {
            override val depth: Int = (parent?.depth ?: 0) + 1
        }

        /**
         * The lexer has read a self-closing tag (e.g., '<foo />') but only returned the [XmlToken.BeginElement] token
         * to the caller. The subsequent [XmlLexer.parseNext] call will return an [XmlToken.EndElement] without
         * actually reading more from the source.
         */
        data class EmptyTag(override val name: XmlToken.QualifiedName, override val parent: OpenTag?) : Tag() {
            override val depth: Int = (parent?.depth ?: 0) + 1
        }
    }

    /**
     * The end of the document is reached. No more data is available.
     */
    object EndOfDocument : LexerState() {
        override val depth = 0
    }
}
