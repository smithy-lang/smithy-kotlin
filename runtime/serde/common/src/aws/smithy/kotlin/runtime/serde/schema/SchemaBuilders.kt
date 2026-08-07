/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * DSL marker for the schema-construction builders, preventing accidental use of an outer builder's
 * receiver inside a nested block.
 */
@DslMarker
public annotation class SchemaDsl

// Effective member traits: the member's own traits merged over its target's (member wins). Computed
// lazily so a recursive/self-referential target is read only after all schemas in the file are built.
private fun memberTraits(ownTraits: List<Trait>, target: () -> Schema): Lazy<Collection<Trait>> = lazy {
    val merged = LinkedHashMap<ShapeId, Trait>()
    for (t in target().traits) merged[t.id] = t
    for (t in ownTraits) merged[t.id] = t
    merged.values.toList()
}

// ── Structure ─────────────────────────────────────────────────────────────────────────────────────

@SchemaDsl
public class StructureSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private val members = mutableListOf<MemberSchema>()

    /** Add a root-level trait to the structure shape. */
    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    /** Declare a member with an eagerly-known [target] shape. */
    public fun member(name: String, target: Schema, vararg traits: Trait): Unit =
        member(name, lazyOf(target), *traits)

    /** Declare a member whose [target] is resolved lazily (used for recursive/cyclic shapes). */
    public fun member(name: String, target: Lazy<Schema>, vararg traits: Trait) {
        val own = traits.toList()
        members += MemberSchemaImpl(
            shapeId = shapeId.withMember(name),
            memberIndex = members.size,
            traits = memberTraits(own) { target.value },
            targetProvider = { target.value },
        )
    }

    internal fun build(): StructureSchema {
        val snapshot = members.toList()
        return StructureSchemaImpl(shapeId, lazy { traits.toList() }) { snapshot }
    }
}

/**
 * Build a [StructureSchema] for [id]. Declare members inside [block] via `member(...)`.
 */
@Suppress("ktlint:standard:function-naming")
public fun StructureSchema(id: ShapeId, block: StructureSchemaBuilder.() -> Unit): StructureSchema {
    return StructureSchemaBuilder(id).apply(block).build()
}

// ── Union ─────────────────────────────────────────────────────────────────────────────────────────

@SchemaDsl
public class UnionSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private val members = mutableListOf<MemberSchema>()

    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    public fun member(name: String, target: Schema, vararg traits: Trait): Unit =
        member(name, lazyOf(target), *traits)

    public fun member(name: String, target: Lazy<Schema>, vararg traits: Trait) {
        val own = traits.toList()
        members += MemberSchemaImpl(
            shapeId = shapeId.withMember(name),
            memberIndex = members.size,
            traits = memberTraits(own) { target.value },
            targetProvider = { target.value },
        )
    }

    internal fun build(): UnionSchema {
        val snapshot = members.toList()
        return UnionSchemaImpl(shapeId, lazy { traits.toList() }) { snapshot }
    }
}

@Suppress("ktlint:standard:function-naming")
public fun UnionSchema(id: ShapeId, block: UnionSchemaBuilder.() -> Unit): UnionSchema {
    return UnionSchemaBuilder(id).apply(block).build()
}

// ── List ──────────────────────────────────────────────────────────────────────────────────────────

@SchemaDsl
public class ListSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private var element: (() -> MemberSchema)? = null

    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    public fun element(target: Schema, vararg traits: Trait): Unit = element(lazyOf(target), *traits)

    public fun element(target: Lazy<Schema>, vararg traits: Trait) {
        val own = traits.toList()
        element = {
            MemberSchemaImpl(
                shapeId = shapeId.withMember("member"),
                memberIndex = 0,
                traits = memberTraits(own) { target.value },
                targetProvider = { target.value },
            )
        }
    }

    internal fun build(): ListSchema {
        val e = requireNotNull(element) { "list schema $shapeId is missing its element" }
        return ListSchemaImpl(shapeId, lazy { traits.toList() }, e)
    }
}

@Suppress("ktlint:standard:function-naming")
public fun ListSchema(id: ShapeId, block: ListSchemaBuilder.() -> Unit): ListSchema {
    return ListSchemaBuilder(id).apply(block).build()
}

// ── Map ───────────────────────────────────────────────────────────────────────────────────────────

@SchemaDsl
public class MapSchemaBuilder internal constructor(private val shapeId: ShapeId) {
    private val traits = mutableListOf<Trait>()
    private var key: (() -> MemberSchema)? = null
    private var value: (() -> MemberSchema)? = null

    public fun trait(trait: Trait) {
        traits.add(trait)
    }

    public fun key(target: Schema, vararg traits: Trait): Unit = key(lazyOf(target), *traits)

    public fun key(target: Lazy<Schema>, vararg traits: Trait) {
        val own = traits.toList()
        key = {
            MemberSchemaImpl(
                shapeId = shapeId.withMember("key"),
                memberIndex = 0,
                traits = memberTraits(own) { target.value },
                targetProvider = { target.value },
            )
        }
    }

    public fun value(target: Schema, vararg traits: Trait): Unit = value(lazyOf(target), *traits)

    public fun value(target: Lazy<Schema>, vararg traits: Trait) {
        val own = traits.toList()
        value = {
            MemberSchemaImpl(
                shapeId = shapeId.withMember("value"),
                memberIndex = 1,
                traits = memberTraits(own) { target.value },
                targetProvider = { target.value },
            )
        }
    }

    internal fun build(): MapSchema {
        val k = requireNotNull(key) { "map schema $shapeId is missing its key" }
        val v = requireNotNull(value) { "map schema $shapeId is missing its value" }
        return MapSchemaImpl(shapeId, lazy { traits.toList() }, k, v)
    }
}

@Suppress("ktlint:standard:function-naming")
public fun MapSchema(id: ShapeId, block: MapSchemaBuilder.() -> Unit): MapSchema {
    return MapSchemaBuilder(id).apply(block).build()
}

// ── Simple ────────────────────────────────────────────────────────────────────────────────────────

/**
 * Build a [SimpleSchema] of [type] for [id] with the given [traits]. Used for named simple shapes (e.g. a
 * modeled string with `@enumValue`s or a member-less standalone simple shape). Prefer [PreludeSchemas]
 * for the unadorned prelude types.
 */
@Suppress("ktlint:standard:function-naming")
public fun SimpleSchema(id: ShapeId, type: ShapeType, vararg traits: Trait): SimpleSchema {
    val own = traits.toList()
    return SimpleSchemaImpl(id, type, lazy { own })
}
