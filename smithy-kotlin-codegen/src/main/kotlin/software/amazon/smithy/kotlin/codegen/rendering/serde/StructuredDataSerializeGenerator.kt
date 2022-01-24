/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.serde

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.OperationShape

interface StructuredDataSerializeGenerator {

    /**
     * Render function responsible for serializing members bound to the payload of the given operation's input shape.
     *
     * The signature is protocol dependent, for example HTTP protocols are passed the execution context and the
     * input type.
     *
     * ```
     * fun serializeFooOperationBody(context: ExecutionContext, input: Foo): HttpBody {
     *  ...
     * }
     * ```
     *
     * Implementations are expected to render a `HttpBody` as the return value.
     *
     * @param ctx the protocol generator context
     * @param op the operation to render serialize for
     * @return the generated symbol which should be a function matching the signature expected for the protocol
     */
    fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): Symbol
}
