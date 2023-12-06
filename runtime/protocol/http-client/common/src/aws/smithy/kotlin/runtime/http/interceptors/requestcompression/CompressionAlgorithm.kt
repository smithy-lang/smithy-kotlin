/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.http.request.HttpRequest

/**
 * Represents a compression algorithm. Used by an interceptor to compress request payloads on operations with the
 * [requestCompression trait](https://smithy.io/2.0/spec/behavior-traits.html#requestcompression-trait)
 */
public interface CompressionAlgorithm {
    /**
     * The ID of the compression algorithm. This will be used to match against the supported compression algorithms
     * of an operation.
     */
    public val id: String

    /**
     * The name of the content encoding to be appended to the `Content-Encoding` header.
     * The [IANA](https://www.iana.org/assignments/http-parameters/http-parameters.xhtml)
     * has a list of registered encodings for reference.
     */
    public val contentEncoding: String

    /**
     * Compresses a HTTP request's body
     */
    public fun compress(request: HttpRequest): HttpRequest
}
