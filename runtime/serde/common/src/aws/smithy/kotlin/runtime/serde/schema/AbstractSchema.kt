/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

/**
 * Internal shared base for every concrete schema implementation. Holds the trait storage so that it stays
 * out of the public sealed [Schema] interface and out of every individual impl.
 *
 * Traits are resolved lazily: for a [MemberSchema] the effective traits (member traits merged over target
 * traits) can't be built until the target is resolvable, which — for recursive shapes — is only safe
 * after construction.
 *
 * @param traits resolves this shape's effective traits.
 */
internal abstract class AbstractSchema(
    traits: Lazy<Collection<Trait>>,
) : Schema {
    final override val traits: Collection<Trait> by traits
}
