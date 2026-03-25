/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

public interface ShapeId {
    public val namespace: String
    public val name: String
    public val fullId: String
}

private data class ShapeIdImpl(override val namespace: String, override val name: String) : ShapeId {
    override val fullId: String = "$namespace#$name"
    override fun toString(): String = fullId
}

public fun ShapeId(namespace: String, name: String): ShapeId = ShapeIdImpl(namespace, name)

public interface MemberShapeId : ShapeId {
    public val memberName: String
}

private data class MemberShapeIdImpl(
    override val namespace: String,
    override val name: String,
    override val memberName: String,
) : MemberShapeId {
    override val fullId: String = "$namespace#$name\$$memberName"
    override fun toString(): String = fullId
}

public fun ShapeId(
    namespace: String,
    name: String,
    memberName: String,
): MemberShapeId = MemberShapeIdImpl(namespace, name, memberName)
