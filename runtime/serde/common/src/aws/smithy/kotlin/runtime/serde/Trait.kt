/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.content.Document

public interface Trait : Shape {
    public val value: Document?
}

private data class TraitImpl(
    override val shapeId: ShapeId,
    override val value: Document?,
    override val traits: List<Trait>,
) : Trait

public fun Trait(
    shapeId: ShapeId,
    value: Document?,
    traits: List<Trait>,
): Trait = TraitImpl(shapeId, value, traits.toList())
