/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.serde.schema.trait.Trait

public sealed interface ListSchema : Schema {
    public val element: MemberSchema
}

internal class ListSchemaImpl(
    override val shapeId: ShapeId,
    override val traits: Collection<Trait>,
    override val element: MemberSchema,
) : ListSchema {
    override val type: ShapeType = ShapeType.LIST
    override fun toString(): String = "ListSchema($shapeId)"
}

/**
 * Create a [ListSchema] whose element is [element].
 *
 * Per the Smithy spec a list has exactly one member, named `member`, so [element] must be identified by this list's
 * `$member` shape id.
 */
public fun ListSchema(id: ShapeId, element: MemberSchema, vararg traits: Trait): ListSchema {
    val expected = id.withMember("member")
    require(element.shapeId == expected) { "list $id element must be $expected, was ${element.shapeId}" }
    return ListSchemaImpl(id, traits.toList(), element)
}
