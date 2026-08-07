/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * The immutable identifier of a Smithy shape.
 *
 * A shape id is composed of a [namespace] and a [name] (`namespace#name`) and, for member shapes,
 * a [member] suffix (`namespace#name$member`). It is purely descriptive model metadata carried by a
 * [Schema] and carries no reference to any generated Kotlin type.
 */
public class ShapeId private constructor(
    public val namespace: String,
    public val name: String,
    public val member: String?,
) {
    public companion object {
        /**
         * Parse a [ShapeId] from its absolute string form.
         *
         * Accepts `namespace#name` for shapes and `namespace#name$member` for member shapes.
         */
        public fun from(absoluteId: String): ShapeId {
            val hash = absoluteId.indexOf('#')
            require(hash > 0 && hash < absoluteId.length - 1) {
                "invalid shape id '$absoluteId': expected 'namespace#name' or 'namespace#name\$member'"
            }
            val namespace = absoluteId.substring(0, hash)
            val rest = absoluteId.substring(hash + 1)
            val dollar = rest.indexOf('$')
            return if (dollar < 0) {
                ShapeId(namespace, rest, null)
            } else {
                require(dollar in 1 until rest.length - 1) {
                    "invalid member shape id '$absoluteId': member name must be non-empty"
                }
                ShapeId(namespace, rest.substring(0, dollar), rest.substring(dollar + 1))
            }
        }

        /**
         * Construct a [ShapeId] from its component parts.
         */
        public fun from(namespace: String, name: String, member: String? = null): ShapeId {
            require(namespace.isNotEmpty()) { "namespace must not be empty" }
            require(name.isNotEmpty()) { "name must not be empty" }
            require(member == null || member.isNotEmpty()) { "member must not be empty when present" }
            return ShapeId(namespace, name, member)
        }
    }

    /**
     * Returns a [ShapeId] identifying the given [member] of this (container) shape.
     */
    public fun withMember(member: String): ShapeId = from(namespace, name, member)

    /**
     * The absolute string form of this shape id: `namespace#name` or `namespace#name$member`.
     */
    public val absoluteId: String = buildString {
        append(namespace)
        append('#')
        append(name)
        if (member != null) {
            append('$')
            append(member)
        }
    }

    override fun toString(): String = absoluteId

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ShapeId &&
                    namespace == other.namespace &&
                    name == other.name &&
                    member == other.member
                )

    override fun hashCode(): Int {
        var result = namespace.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + (member?.hashCode() ?: 0)
        return result
    }
}
