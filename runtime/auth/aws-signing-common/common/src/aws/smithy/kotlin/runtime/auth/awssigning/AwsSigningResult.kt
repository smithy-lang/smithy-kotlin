/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.awssigning

/**
 * The result of an AWS signing operation
 * @param T The type of the result
 */
public data class AwsSigningResult<T>(
    /**
     * The signed output.
     */
    public val output: T,

    /**
     * The signature value from the result. Depending on the requested signature type and algorithm, this value will be
     * in one of the following formats:
     * * `HTTP_REQUEST_VIA_HEADERS` - hex encoding of the binary signature value
     * * `HTTP_REQUEST_VIA_QUERY_PARAMS` - hex encoding of the binary signature value
     * * `HTTP_REQUEST_CHUNK/SIGV4` - hex encoding of the binary signature value
     * * `HTTP_REQUEST_CHUNK/SIGV4_ASYMMETRIC` - '*'-padded hex encoding of the binary signature value
     * * `HTTP_REQUEST_EVENT` - hex encoding of the binary signature value
     */
    public val signature: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AwsSigningResult<*>

        if (output != other.output) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = output.hashCode()
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
