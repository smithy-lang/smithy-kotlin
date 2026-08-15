/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.UnionShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * CBOR-specific [SerializeUnionGenerator] that emits definite-length arrays/maps for union list/map members. See
 * [CborSerializeStructGenerator] for the rationale.
 */
class CborSerializeUnionGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    shape: UnionShape,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format,
) : SerializeUnionGenerator(ctx, shape, members, writer, defaultTimestampFormat) {
    override fun containerSizeArg(collectionExpr: String, canBeNull: Boolean): String =
        if (canBeNull) "" else ", $collectionExpr.size"
}
