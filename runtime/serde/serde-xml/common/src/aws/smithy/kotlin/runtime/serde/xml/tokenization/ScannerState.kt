/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.tokenization

import aws.smithy.kotlin.runtime.serde.xml.XmlToken

/**
 * Describes an internal state of a [XmlScanner].
 */
sealed class ScannerState {
    /**
     * The node depth at which the scanner is parsing tokens. Like the concept of depth in [XmlToken], this depth is
     * 1 at the root (but 0 outside the root).
     */
    abstract val depth: Int

    /**
     * The initial state at the beginning of a document before reading any tags, DTD, or prolog.
     */
    object Initial : ScannerState() {
        override val depth = 0
    }

    /**
     * The scanner is expecting the root tag next.
     */
    object BeforeRootTag : ScannerState() {
        override val depth = 0
    }

    /**
     * Describes the state of being inside a tag.
     */
    sealed class Tag : ScannerState() {
        override val depth: Int by lazy { (parent?.depth ?: 0) + 1 }

        abstract val name: XmlToken.QualifiedName
        abstract val parent: OpenTag?

        /**
         * The scanner is inside a tag. The next close tag should match the name of this tag.
         */
        data class OpenTag(
            override val name: XmlToken.QualifiedName,
            override val parent: OpenTag?,
            val seenChildren: Boolean,
        ) : Tag()

        /**
         * The scanner has read a self-closing tag (e.g., '<foo />') but only returned the [XmlToken.BeginElement] token
         * to the caller. The subsequent [XmlScanner.parseNext] call will return an [XmlToken.EndElement] without
         * actually reading more from the source.
         */
        data class EmptyTag(override val name: XmlToken.QualifiedName, override val parent: OpenTag?) : Tag()
    }

    /**
     * The end of the document is reached. No more data is available.
     */
    object EndOfDocument : ScannerState() {
        override val depth = 0
    }
}
