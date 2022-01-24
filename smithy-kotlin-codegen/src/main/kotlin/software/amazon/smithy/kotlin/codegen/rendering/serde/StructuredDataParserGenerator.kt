/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.StructureShape

interface StructuredDataParserGenerator {

    /**
     * Render function responsible for deserializing members bound to the payload of the given output shape.
     *
     * The signature is protocol dependent, for example HTTP protocols are passed the output type builder and
     * the HTTP response object:
     *
     * ```
     * private fun deserializeFooOperationBody(builder: Foo.Builder, response: HttpResponse) {
     *    ...
     * }
     * ```
     *
     * Implementations are expected to instantiate an appropriate deserializer for the protocol and deserialize
     * the output shape from the payload using the builder passed in.
     *
     * @param ctx the protocol generator context
     * @param op the operation to render deserialize for
     * @return the generated symbol which should be a function matching the signature expected for the protocol
     */
    fun operationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol

    /**
     * Render function responsible for deserializing members bound to the payload for the given error shape.
     *
     * The signature is protocol dependent, for example HTTP protocols are passed the output type builder and
     * the HTTP response object:
     *
     * ```
     * fun deserializeFooError(builder: FooError.Builder, response: HttpResponse) {
     *     ...
     * }
     * ```
     *
     * @param ctx the protocol generator context
     * @param errorShape the error shape to render deserialize for
     * @return the generated symbol which should be a function matching the signature expected for the protocol
     */
    fun errorDeserializer(ctx: ProtocolGenerator.GenerationContext, errorShape: StructureShape): Symbol
}
