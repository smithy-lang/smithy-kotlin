/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * Static, format-neutral runtime metadata describing *what* a shape is.
 *
 * A [Schema] tells a `ShapeSerializer`/`ShapeDeserializer` how to (de)serialize a shape without baking in
 * any wire format. It carries the shape's [shapeId], [type], and its serde-relevant [traits], and — for
 * aggregate shapes — the member graph. A schema contains shape information only: it holds no reference to
 * any generated Kotlin type and is not parameterized on one.
 *
 * The hierarchy is sealed with one subtype per Smithy shape kind, so engine dispatch can be an exhaustive
 * `when` with smart casts and collection/member navigation is non-null by construction.
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
     * The effective serde-relevant traits on this shape. For a [MemberSchema] these are the member's own
     * traits merged over its target's traits (member wins).
     *
     * Look a trait up by id with [getTrait]/[hasTrait].
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

// ── Aggregates ──────────────────────────────────────────────────────────────────────────────────────

/**
 * A structure shape. Iterate [members] to walk fields; look one up by [member] name or index.
 */
public sealed interface StructureSchema : Schema {
    public val members: List<MemberSchema>

    /**
     * The member named [name], or `null` if this structure has no such member.
     */
    public fun member(name: String): MemberSchema?

    /**
     * The member at positional [index], or `null` if out of range. The index is an internal performance
     * aid ([MemberSchema.memberIndex]); callers must not persist or interpret its value.
     */
    public fun member(index: Int): MemberSchema?
}

/**
 * A union shape. Exactly one member is set at a time.
 */
public sealed interface UnionSchema : Schema {
    public val members: List<MemberSchema>
    public fun member(name: String): MemberSchema?
    public fun member(index: Int): MemberSchema?
}

/**
 * A list (or set) shape. [element] is the member describing the list's element shape.
 */
public sealed interface ListSchema : Schema {
    public val element: MemberSchema
}

/**
 * A map shape. [key] and [value] are members describing the map's key and value shapes.
 */
public sealed interface MapSchema : Schema {
    public val key: MemberSchema
    public val value: MemberSchema
}

// ── Member ──────────────────────────────────────────────────────────────────────────────────────────

/**
 * A member shape: a named slot in a structure/union/list/map that points at a [target] shape.
 *
 * Member and target are kept distinct (1:1 with the Smithy model), so member-only traits and the target's
 * traits stay separable while [traits] still exposes the effective merge.
 */
public sealed interface MemberSchema : Schema {
    /**
     * The member's name as declared in the model (never a wire name like `jsonName`).
     */
    public val memberName: String

    /**
     * The positional index of this member within its containing shape. An internal performance aid;
     * callers must not persist or interpret its value.
     */
    public val memberIndex: Int

    /**
     * The shape this member points at.
     */
    public val target: Schema
}

// ── Simple types ────────────────────────────────────────────────────────────────────────────────────

/**
 * A Smithy simple shape (string, boolean, numeric, blob, timestamp, document, enum, intEnum).
 */
public sealed interface SimpleSchema : Schema
