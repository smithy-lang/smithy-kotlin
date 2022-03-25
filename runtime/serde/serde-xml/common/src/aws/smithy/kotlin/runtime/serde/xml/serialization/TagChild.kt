/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml.serialization

/**
 * Represents a child node of an XML tag.
 */
sealed class TagChild {
    /**
     * A child tag node.
     * @param lazyTagWriter The [LazyTagWriter] for the given child.
     */
    data class Tag(val lazyTagWriter: LazyTagWriter) : TagChild()

    /**
     * A child text node.
     * @param text The text of the node.
     */
    data class Text(val text: String) : TagChild()
}
