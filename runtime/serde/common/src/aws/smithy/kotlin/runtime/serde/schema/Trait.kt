/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import aws.smithy.kotlin.runtime.content.Document

/**
 * A serde-relevant Smithy trait attached to a [Schema].
 *
 * Only the subset of Smithy traits that influence (de)serialization are represented at runtime (e.g.
 * `jsonName`, `xmlName`, HTTP binding traits, `timestampFormat`). Each trait is identified by the [id] of
 * its trait shape, which is how a schema looks it up.
 */
public interface Trait {
    /**
     * The Smithy shape id of the trait's definition (e.g. `smithy.api#jsonName`). Used as the lookup key
     * on a [Schema].
     */
    public val id: ShapeId
}

/**
 * A trait whose value is not modeled by a dedicated runtime type. Unknown or custom traits are
 * represented generically as a [Document] so they remain enumerable via [Schema.traits] without a
 * bespoke class per trait.
 */
public class DocumentTrait(
    override val id: ShapeId,
    /**
     * The trait's node value, or `null` for annotation (valueless) traits.
     */
    public val value: Document?,
) : Trait {
    override fun toString(): String = "DocumentTrait($id=$value)"
}
