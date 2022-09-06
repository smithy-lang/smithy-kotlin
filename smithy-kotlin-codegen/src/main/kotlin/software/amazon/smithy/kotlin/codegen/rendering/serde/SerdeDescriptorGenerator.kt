/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

/**
 * Renders the serde object/field descriptors for a shape
 *
 * NOTE: This is a fragment generator that is expected to be used in the context of generating
 * a serializer or deserializer implementation.
 */
interface SerdeDescriptorGenerator {
    fun render()
}
