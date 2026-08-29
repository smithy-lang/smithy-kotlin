/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.TimestampFormatTrait
import aws.smithy.kotlin.runtime.serde.schema.trait.Trait
import aws.smithy.kotlin.runtime.time.TimestampFormat

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
 * The trait identified by [id] declared directly on this shape, or `null` if absent.
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Trait> Schema.getTrait(id: ShapeId): T? = traits.find { it.id == id } as T?

/**
 * True if a trait identified by [id] is present on this shape.
 */
public fun Schema.hasTrait(id: ShapeId): Boolean = traits.any { it.id == id }

/**
 * The *effective* trait identified by [id]: the one declared on this shape if present, otherwise — for a
 * [MemberSchema] only — the one declared on the shape the member targets.
 *
 * A shape-level trait such as `@timestampFormat` may be modeled either on a member or on the shape that member
 * targets, while serde walks members, so a lookup that must honor the modeled value goes through here. The member
 * is the more specific declaration and wins.
 */
@Suppress("UNCHECKED_CAST")
public fun <T : Trait> Schema.getEffectiveTrait(id: ShapeId): T? = getTrait(id) ?: (this as? MemberSchema)?.target?.getTrait(id)

/**
 * The format a timestamp described by this shape is written and read with: the format requested via
 * `@timestampFormat`, otherwise [default].
 *
 * The trait is resolved as an effective trait, since a model may declare it either on the member being serialized
 * or on the timestamp shape that member targets.
 */
public fun Schema.resolveTimestampFormat(default: TimestampFormat): TimestampFormat = getEffectiveTrait<TimestampFormatTrait>(TimestampFormatTrait.ID)?.format ?: default

/** DSL marker keeping an outer builder's receiver out of scope inside a nested block. */
@DslMarker
public annotation class SchemaDsl
