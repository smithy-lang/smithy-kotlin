/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression

import aws.smithy.kotlin.runtime.http.HttpBody

/**
 * Represents a compression algorithm to be used for compressing request payloads on qualifying operations
 */
public interface CompressionAlgorithm {
    /**
     * The ID of the compression algorithm
     */
    public val id: String

    /**
     * Compresses a payload
     */
    public suspend fun compress(stream: HttpBody): HttpBody
}
