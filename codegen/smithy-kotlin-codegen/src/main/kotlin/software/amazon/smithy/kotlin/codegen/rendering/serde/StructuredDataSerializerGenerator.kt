/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.StructureShape

/**
 * Responsible for rendering serialization of structured data (e.g. json, yaml, xml).
 */
interface StructuredDataSerializerGenerator {

    /**
     * Render function responsible for serializing members bound to the payload of the given operation's input shape.
     *
     * ```
     * fun serializeFooOperationBody(context: ExecutionContext, input: Foo): ByteArray {
     *  ...
     * }
     * ```
     *
     * Implementations are expected to serialize to the specific data format and return the contents as a byte array.
     *
     * @param ctx the protocol generator context
     * @param op the operation to render serialize for
     * @param members the members of the operation's input shape that are bound to the payload. Not all members are
     * bound to the document, some may be bound to e.g. headers, uri, etc.
     * @return the generated symbol which should be a function matching the expected signature
     */
    fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol

    /**
     * Render function responsible for serializing the given member shape as the payload.
     *
     * ```
     * fun serializeFooPayload(input: Foo): ByteArray {
     *  ...
     * }
     * ```
     *
     * Implementations are expected to serialize to the specific data format and return the contents as a byte array.
     *
     * @param ctx the protocol generator context
     * @param shape the shape or member to serialize
     * @param members the subset of members to serialize in the payload
     * @return the generated symbol which should be a function matching the expected signature
     */
    fun payloadSerializer(
        ctx: ProtocolGenerator.GenerationContext,
        shape: Shape,
        members: Collection<MemberShape>? = null,
    ): Symbol

    /**
     * Render function responsible for serializing members bound to the payload for the given error shape.
     *
     * Because only a subset of fields of an operation error may be bound to the payload a builder is given
     * as an argument.
     *
     * ```
     * fun serializeFooError(builder: FooError.Builder, payload: ByteArray) {
     *     ...
     * }
     * ```
     *
     * Implementations are expected to instantiate an appropriate serializer for the protocol and serialize
     * the error shape from the payload using the builder passed in.
     *
     * @param ctx the protocol generator context
     * @param errorShape the error shape to render deserialize for
     * @param members the members of the error shape that are bound to the payload. Not all members are
     * bound to the document, some may be bound to e.g. headers, status code, etc
     * @return the generated symbol which should be a function matching the signature expected for the protocol
     */
    fun errorSerializer(ctx: ProtocolGenerator.GenerationContext, errorShape: StructureShape, members: List<MemberShape>): Symbol
}
