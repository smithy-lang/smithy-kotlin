/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.rendering.serde

/**
 * Renders the serde object/field descriptors for a shape
 *
 * NOTE: This is a fragment generator that is expected to be used in the context of generating
 * a serializer or deserializer implementation.
 */
interface SerdeDescriptorGenerator {
    /**
     * Render the object/field descriptors for the shape.
     *
     * Descriptors are emitted as file-scoped `private val` declarations and are expected to be rendered outside of
     * (immediately before) the serde function so they are constructed once rather than on every invocation.
     */
    fun render()
}
