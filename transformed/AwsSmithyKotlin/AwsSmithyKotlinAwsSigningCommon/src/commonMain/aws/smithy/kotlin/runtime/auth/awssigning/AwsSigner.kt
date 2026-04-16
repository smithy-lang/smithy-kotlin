/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.request.HttpRequest

/**
 * A component capable of signing requests and request chunks for AWS APIs.
 */
public interface AwsSigner {
    /**
     * Signs an HTTP request according to the supplied signing configuration
     * @param request The request to sign
     * @param config The signing configuration
     * @return The signed request
     */
    public suspend fun sign(request: HttpRequest, config: AwsSigningConfig): AwsSigningResult<HttpRequest>

    /**
     * Signs a body chunk according to the supplied signing configuration
     * @param chunkBody The chunk payload to sign
     * @param prevSignature The signature of the previous component of the request (either the initial request itself
     * for the first chunk or the previous chunk otherwise)
     * @param config The signing configuration
     * @return The signing result, which provides access to all signing-related result properties
     */
    public suspend fun signChunk(
        chunkBody: ByteArray,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit>

    /**
     * Signs a chunked payload's trailer according to the supplied signing configuration
     * @param trailingHeaders  the trailing [Headers] to send
     * @param prevSignature The signature of the previous componenet of the request (in most cases, this is the signature of the final chunk)
     * @param config The signing configuration
     * @return The signing result, which should be appended as a trailing header itself, named `x-amz-trailer-signature`
     */
    public suspend fun signChunkTrailer(
        trailingHeaders: Headers,
        prevSignature: ByteArray,
        config: AwsSigningConfig,
    ): AwsSigningResult<Unit>
}
