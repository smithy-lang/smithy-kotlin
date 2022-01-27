/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape

/**
 * Responsible for rendering deserialization of structured data (e.g. json, yaml, xml).
 */
interface StructuredDataParserGenerator {

    /**
     * Render the function responsible for deserializing members bound to the payload of the given output shape.
     *
     * Because only a subset of fields of an operation output may be bound to the payload a builder is given
     * as an argument.
     *
     * ```
     * private fun deserializeFooOperationBody(builder: Foo.Builder, payload: ByteArray) {
     *     ...
     * }
     * ```
     *
     * Implementations are expected to instantiate an appropriate deserializer for the protocol and deserialize
     * the output shape from the payload using the builder passed in.
     *
     * @param ctx the protocol generator context
     * @param op the operation to render deserialize for
     * @param members the members of the operation's output shape that are bound to the payload. Not all members are
     * bound to the document, some may be bound to e.g. headers, status code, etc
     * @return the generated symbol which should be a function matching the signature expected for the protocol
     */
    fun operationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol

    /**
     * Render function responsible for deserializing members bound to the payload for the given error shape.
     *
     * Because only a subset of fields of an operation error may be bound to the payload a builder is given
     * as an argument.
     *
     * ```
     * fun deserializeFooError(builder: FooError.Builder, payload: ByteArray) {
     *     ...
     * }
     * ```
     *
     * Implementations are expected to instantiate an appropriate deserializer for the protocol and deserialize
     * the error shape from the payload using the builder passed in.
     *
     * @param ctx the protocol generator context
     * @param errorShape the error shape to render deserialize for
     * @param members the members of the error shape that are bound to the payload. Not all members are
     * bound to the document, some may be bound to e.g. headers, status code, etc
     * @return the generated symbol which should be a function matching the signature expected for the protocol
     */
    fun errorDeserializer(ctx: ProtocolGenerator.GenerationContext, errorShape: StructureShape, members: List<MemberShape>): Symbol
}
