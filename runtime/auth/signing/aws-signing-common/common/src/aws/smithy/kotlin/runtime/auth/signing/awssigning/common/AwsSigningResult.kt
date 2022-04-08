/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.common

import aws.smithy.kotlin.runtime.http.request.HttpRequest

/**
 * Contains the result of AWS signing. Depending on the signing configuration, not all members may be assigned and some
 * members (e.e., [signature]) may have a variable format.
 */
data class AwsSigningResult(
    /**
     * The signed HTTP request from the result, may be null if an HTTP request was not signed.
     */
    val signedRequest: HttpRequest?,

    /**
     * The signature value from the result. Depending on the requested signature type and algorithm, this value will be
     * in one of the following formats:
     * * `HTTP_REQUEST_VIA_HEADERS` - hex encoding of the binary signature value
     * * `HTTP_REQUEST_VIA_QUERY_PARAMS` - hex encoding of the binary signature value
     * * `HTTP_REQUEST_CHUNK/SIGV4` - hex encoding of the binary signature value
     * * `HTTP_REQUEST_CHUNK/SIGV4_ASYMMETRIC` - '*'-padded hex encoding of the binary signature value
     * * `HTTP_REQUEST_EVENT` - binary signature value
     */
    val signature: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as AwsSigningResult

        if (signedRequest != other.signedRequest) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = signedRequest?.hashCode() ?: 0
        result = 31 * result + signature.contentHashCode()
        return result
    }
}
