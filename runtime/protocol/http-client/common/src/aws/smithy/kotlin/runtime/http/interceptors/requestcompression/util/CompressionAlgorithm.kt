package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.util

import aws.smithy.kotlin.runtime.http.HttpBody

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