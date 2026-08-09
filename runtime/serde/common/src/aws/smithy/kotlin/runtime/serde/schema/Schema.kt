/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

/**
 * Static, format-neutral runtime metadata describing *what* a shape is.
 */
public sealed interface Schema {
    /**
     * The Smithy shape id of this shape.
     */
    public val shapeId: ShapeId

    /**
     * The kind of this shape.
     */
    public val type: ShapeType

    /**
     * The traits associated with this shape
     */
    public val traits: Collection<Trait>
}

/**
 * The trait identified by [id] on this shape, or `null` if absent.
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Trait> Schema.getTrait(id: ShapeId): T? = traits.find { it.id == id } as T?

/**
 * True if a trait identified by [id] is present on this shape.
 */
public fun Schema.hasTrait(id: ShapeId): Boolean = traits.any { it.id == id }

/** DSL marker keeping an outer builder's receiver out of scope inside a nested block. */
@DslMarker
public annotation class SchemaDsl
