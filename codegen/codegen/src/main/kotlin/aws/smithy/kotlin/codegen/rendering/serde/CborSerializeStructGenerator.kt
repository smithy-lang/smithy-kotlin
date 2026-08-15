/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.serde

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * CBOR-specific [SerializeStructGenerator] that emits definite-length arrays/maps whenever the element/entry count is
 * known at the call site. Encoding a definite length avoids the per-container "break" byte and lets the peer allocate
 * exactly-sized collections up front. When the count is not statically available (e.g. a sparse collection element
 * that may be null) it falls back to the base indefinite-length encoding.
 */
class CborSerializeStructGenerator(
    ctx: ProtocolGenerator.GenerationContext,
    members: List<MemberShape>,
    writer: KotlinWriter,
    defaultTimestampFormat: TimestampFormatTrait.Format,
) : SerializeStructGenerator(ctx, members, writer, defaultTimestampFormat) {
    override fun containerSizeArg(collectionExpr: String, canBeNull: Boolean): String =
        if (canBeNull) "" else ", $collectionExpr.size"
}
