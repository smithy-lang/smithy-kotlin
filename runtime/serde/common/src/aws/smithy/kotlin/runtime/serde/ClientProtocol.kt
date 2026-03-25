/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.serde.codecs.Codec
import aws.smithy.kotlin.runtime.serde.schemas.OperationSchema

public interface ClientProtocol<Req, Res> : Shape {
    public val payloadCodec: Codec

    public fun <I, O> createRequest(
        op: OperationSchema<I, O>,
        input: I,
        context: ExecutionContext,
        endpoint: Endpoint,
    ): Req

    public fun <I, O> createResponse(
        op: OperationSchema<I, O>,
        input: I,
        output: O,
        context: ExecutionContext,
    ): Res

    public fun setEndpoint(request: Req, endpoint: Endpoint)
}
