/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.tracing.NoOpTraceSpan
import aws.smithy.kotlin.runtime.tracing.TraceSpan

/**
 * A component capable of signing requests and request chunks for AWS APIs.
 */
public interface AwsSigner {
    /**
     * Signs an HTTP request according to the supplied signing configuration
     * @param request The request to sign
     * @param config The signing configuration
     * @param traceSpan The span to which tracing events will be emitted. Defaults to [NoOpTraceSpan] if none is
     * specified.
     * @return The signed request
     */
    public suspend fun sign(
        request: HttpRequest,
        config: AwsSigningConfig,
        traceSpan: TraceSpan = NoOpTraceSpan,
    ): AwsSigningResult<HttpRequest>

    /**
     * Signs a body chunk according to the supplied signing configuration
     * @param chunkBody The chunk payload to sign
     * @param prevSignature The signature of the previous component of the request (either the initial request itself
     * for the first chunk or the previous chunk otherwise)
     * @param config The signing configuration
     * @param traceSpan The span to which tracing events will be emitted. Defaults to [NoOpTraceSpan] if none is
     * specified.
     * @return The signing result, which provides access to all signing-related result properties
     */
    public suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
        traceSpan: TraceSpan = NoOpTraceSpan,
    ): AwsSigningResult<Unit>
}
