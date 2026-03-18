/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde

import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.operation.ExecutionContext

public interface ClientProtocol<Req, Res> : Shape {
    public val payloadCodec: Codec

    public fun createRequest(
        op: OperationSchema,
        input: Any, // FIXME
        context: ExecutionContext,
        endpoint: Endpoint,
    ): Req

    public fun createResponse(
        op: OperationSchema,
        input: Any, // FIXME
        output: Any, // FIXME
        context: ExecutionContext,
    ): Res

    public fun setEndpoint(request: Req, endpoint: Endpoint)
}
