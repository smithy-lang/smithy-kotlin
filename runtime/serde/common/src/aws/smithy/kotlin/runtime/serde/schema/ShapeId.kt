/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * The immutable identifier of a Smithy shape.
 */
public sealed interface ShapeId {
    public val namespace: String
    public val name: String

    /** The absolute string form of this id. */
    public val absoluteId: String
}

/**
 * A member shape id.
 */
public sealed interface MemberShapeId : ShapeId {
    public val member: String
}

/** The [MemberShapeId] for [member] of this shape, replacing any existing member component. */
public fun ShapeId.withMember(member: String): MemberShapeId = MemberShapeIdImpl(namespace, name, member)

private data class ShapeIdImpl(
    override val namespace: String,
    override val name: String,
) : ShapeId {
    init {
        require(namespace.isNotEmpty()) { "namespace must not be empty" }
        require(name.isNotEmpty()) { "name must not be empty" }
    }
    override val absoluteId: String = "$namespace#$name"
    override fun toString(): String = absoluteId
}

private data class MemberShapeIdImpl(
    override val namespace: String,
    override val name: String,
    override val member: String,
) : MemberShapeId {
    init {
        require(namespace.isNotEmpty()) { "namespace must not be empty" }
        require(name.isNotEmpty()) { "name must not be empty" }
        require(member.isNotEmpty()) { "member must not be empty" }
    }
    override val absoluteId: String = "$namespace#$name\$$member"
    override fun toString(): String = absoluteId
}

/**
 * Parse a shape id from its absolute form `namespace#name` (or `namespace#name$member`, yielding a
 * [MemberShapeId]).
 *
 * @throws IllegalArgumentException if [absoluteId] is not a well-formed shape id.
 */
public fun shapeId(absoluteId: String): ShapeId {
    val hash = absoluteId.indexOf('#')
    require(hash > 0 && hash < absoluteId.length - 1) {
        "invalid shape id '$absoluteId': expected 'namespace#name' or 'namespace#name\$member'"
    }
    val namespace = absoluteId.substring(0, hash)

    // scan '$' in the original string (after '#') to avoid allocating an intermediate substring
    val dollar = absoluteId.indexOf('$', hash + 1)
    if (dollar < 0) {
        return ShapeIdImpl(namespace, absoluteId.substring(hash + 1))
    } else {
        require(dollar > hash + 1 && dollar < absoluteId.length - 1) {
            "invalid member shape id '$absoluteId': member name must be non-empty"
        }
        return MemberShapeIdImpl(namespace, absoluteId.substring(hash + 1, dollar), absoluteId.substring(dollar + 1))
    }
}

/** Construct a [MemberShapeId] from parts. */
public fun shapeId(namespace: String, name: String, member: String): MemberShapeId = MemberShapeIdImpl(namespace, name, member)

/** Construct a [ShapeId] from parts. */
public fun shapeId(namespace: String, name: String): ShapeId = ShapeIdImpl(namespace, name)
