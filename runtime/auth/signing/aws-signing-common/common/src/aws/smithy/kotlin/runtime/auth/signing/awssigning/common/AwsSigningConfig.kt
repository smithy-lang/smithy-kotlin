/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.common

import aws.smithy.kotlin.runtime.auth.credentials.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.time.Duration

typealias ShouldSignHeaderPredicate = (String) -> Boolean

enum class AwsSigningAlgorithm {
    SIGV4,
    SIGV4_ASYMMETRIC,
}

enum class AwsSignatureType {
    HTTP_REQUEST_VIA_HEADERS,
    HTTP_REQUEST_VIA_QUERY_PARAMS,
    HTTP_REQUEST_CHUNK,
    HTTP_REQUEST_EVENT,
}

sealed class BodyHashSource(open val hash: String?) {
    object CalculateFromPayload : BodyHashSource(null)
    object UnsignedPayload : BodyHashSource("UNSIGNED-PAYLOAD")
    object EmptyBody : BodyHashSource("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") // hash of ""
    object StreamingAws4HmacSha256Payload : BodyHashSource("STREAMING-AWS4-HMAC-SHA256-PAYLOAD")
    object StreamingAws4HmacSha256Events : BodyHashSource("STREAMING-AWS4-HMAC-SHA256-EVENTS")
    data class Precalculated(override val hash: String) : BodyHashSource(hash)
}

enum class AwsSignedBodyHeader {
    /**
     * Do not add a header.
     */
    NONE,

    /**
     * Add the `x-amz-content-sha256` header with the canonical request's body value.
     */
    X_AMZ_CONTENT_SHA256,
}

/**
 * The configuration for an individual signing operation.
 */
class AwsSigningConfig(builder: Builder) {
    companion object {
        operator fun invoke(block: Builder.() -> Unit): AwsSigningConfig = Builder().apply(block).build()
    }

    /**
     * The region for which requests will be signed.
     */
    val region: String = requireNotNull(builder.region) { "Signing config must specify a region" }

    /**
     * The name of service for which requests will be signed.
     */
    val service: String = requireNotNull(builder.service) { "Signing config must specify a service" }

    /**
     * Indicates the signing date/timestamp to use for the signature. Defaults to the current date at config
     * construction time.
     */
    val signingDate: Instant = builder.signingDate ?: Instant.now()

    /**
     * A predicate to control which headers are a part of the canonical request. Note that skipping auth-required
     * headers will result in an unusable signature. Headers injected by the signing process cannot be skipped.
     *
     * This function does not override the internal check function (e.g., for `x-amzn-trace-id`, `user-agent`, etc.) but
     * rather supplements it. In particular, a header will get signed if and only if it returns true to both the
     * internal check and this function (if defined).
     *
     * The default predicate is to approve all headers (i.e., `_ -> true`).
     */
    val shouldSignHeader: ShouldSignHeaderPredicate = builder.shouldSignHeader

    /**
     * The algorithm to use when signing requests.
     */
    val algorithm: AwsSigningAlgorithm = builder.algorithm

    /**
     * Indicates what type of signature to compute.
     */
    val signatureType: AwsSignatureType = builder.signatureType

    /**
     * Normally we assume the URI will be encoded once in preparation for transmission. Certain services do not decode
     * before checking signature, requiring the URI to be double-encoded in the canonical request in order to match a
     * signature check.
     */
    val useDoubleUriEncode: Boolean = builder.useDoubleUriEncode

    /**
     * Controls whether URI paths should be normalized when building the canonical request.
     */
    val normalizeUriPath: Boolean = builder.normalizeUriPath

    /**
     * Determines wheter the `X-Amz-Security-Token` query param should be omitted. Normally, this parameter is added
     * during signing if the credentials have a session token. The only known case where this should be true is when
     * signing a websocket handshake to IoT Core.
     */
    val omitSessionToken: Boolean = builder.omitSessionToken

    /**
     * Determines the source of the canonical request's body public value. The default is
     * [BodyHashSource.CalculateFromPayload], indicating that a public value will be calculated from the payload during
     * signing.
     */
    val bodyHashSource: BodyHashSource = builder.bodyHashSource ?: BodyHashSource.CalculateFromPayload

    /**
     * Determines which body "hash" header, if any, should be added to the canonical request and the signed request.
     */
    val signedBodyHeader: AwsSignedBodyHeader = builder.signedBodyHeader

    /**
     * Indicates the AWS credentials provider from which to fetch credentials.
     */
    val credentialsProvider: CredentialsProvider = requireNotNull(builder.credentialsProvider) {
        "Signing config must specify a credentials provider"
    }

    /**
     * Determines how long the signed request should be valid. If non-null and the signing transform is query param,
     * then signing will add `X-Amz-Expires` to the query string, equal to the public value specified here. If this
     * value is null or if header signing is being used then this parameter has no effect. Note that the resolution of
     * expiration is in seconds.
     */
    val expiresAfter: Duration? = builder.expiresAfter

    fun toBuilder(): Builder = Builder().also {
        it.region = region
        it.service = service
        it.signingDate = signingDate
        it.shouldSignHeader = shouldSignHeader
        it.algorithm = algorithm
        it.signatureType = signatureType
        it.useDoubleUriEncode = useDoubleUriEncode
        it.normalizeUriPath = normalizeUriPath
        it.omitSessionToken = omitSessionToken
        it.bodyHashSource = bodyHashSource
        it.signedBodyHeader = signedBodyHeader
        it.credentialsProvider = credentialsProvider
        it.expiresAfter = expiresAfter
    }

    class Builder {
        var region: String? = null
        var service: String? = null
        var signingDate: Instant? = null
        var shouldSignHeader: ShouldSignHeaderPredicate = { _ -> true } // Allow signing all headers by default
        var algorithm: AwsSigningAlgorithm = AwsSigningAlgorithm.SIGV4
        var signatureType: AwsSignatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
        var useDoubleUriEncode: Boolean = true
        var normalizeUriPath: Boolean = true
        var omitSessionToken: Boolean = false
        var bodyHashSource: BodyHashSource? = null
        var signedBodyHeader: AwsSignedBodyHeader = AwsSignedBodyHeader.NONE
        var credentialsProvider: CredentialsProvider? = null
        var expiresAfter: Duration? = null

        fun build(): AwsSigningConfig = AwsSigningConfig(this)
    }
}
