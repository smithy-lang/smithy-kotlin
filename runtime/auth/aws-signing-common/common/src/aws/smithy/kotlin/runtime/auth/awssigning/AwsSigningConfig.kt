/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.time.Instant
import kotlin.time.Duration

public typealias ShouldSignHeaderPredicate = (String) -> Boolean

/**
 * Defines the AWS signature version to use
 */
public enum class AwsSigningAlgorithm {
    /**
     * AWS Signature Version 4
     * see: https://docs.aws.amazon.com/general/latest/gr/signature-version-4.html
     */
    SIGV4,

    /**
     * AWS Signature Version 4 Asymmetric
     */
    SIGV4_ASYMMETRIC,
}

/**
 * Defines the type of signature to compute
 */
public enum class AwsSignatureType {
    /**
     * A signature for a full http request should be computed and applied via headers
     */
    HTTP_REQUEST_VIA_HEADERS,

    /**
     * A signature for a full http request should be computed and applied via query parameters
     */
    HTTP_REQUEST_VIA_QUERY_PARAMS,

    /**
     * Compute a signature for a payload chunk
     */
    HTTP_REQUEST_CHUNK,

    /**
     * Compute a signature for trailing headers
     */
    HTTP_REQUEST_TRAILING_HEADERS,

    /**
     * Compute a signature for an event stream
     */
    HTTP_REQUEST_EVENT,
}

public enum class AwsSignedBodyHeader {
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
public class AwsSigningConfig(builder: Builder) {
    public companion object {
        public inline operator fun invoke(block: Builder.() -> Unit): AwsSigningConfig = Builder().apply(block).build()
    }

    /**
     * The region for which requests will be signed.
     */
    public val region: String = requireNotNull(builder.region) { "Signing config must specify a region" }

    /**
     * The name of service for which requests will be signed.
     */
    public val service: String = requireNotNull(builder.service) { "Signing config must specify a service" }

    /**
     * Indicates the signing date/timestamp to use for the signature. Defaults to the current date at config
     * construction time.
     */
    public val signingDate: Instant = builder.signingDate ?: Instant.now()

    /**
     * A predicate to control which headers are a part of the canonical request. Note that skipping auth-required
     * headers will result in an unusable signature. Headers injected by the signing process cannot be skipped.
     *
     * This function does not override the internal check function (e.g., for `x-amzn-trace-id`, `user-agent`, etc.) but
     * rather supplements it. In particular, a header will get signed if and only if it returns true to both the
     * internal check and this function (if defined).
     *
     * The default predicate is to not reject signing any headers (i.e., `_ -> true`).
     */
    public val shouldSignHeader: ShouldSignHeaderPredicate = builder.shouldSignHeader

    /**
     * The algorithm to use when signing requests.
     */
    public val algorithm: AwsSigningAlgorithm = builder.algorithm

    /**
     * Indicates what type of signature to compute.
     */
    public val signatureType: AwsSignatureType = builder.signatureType

    /**
     * Normally we assume the URI will be encoded once in preparation for transmission. Certain services do not decode
     * before checking signature, requiring the URI to be double-encoded in the canonical request in order to match a
     * signature check.
     */
    public val useDoubleUriEncode: Boolean = builder.useDoubleUriEncode

    /**
     * Controls whether URI paths should be normalized when building the canonical request.
     */
    public val normalizeUriPath: Boolean = builder.normalizeUriPath

    /**
     * Determines whether the `X-Amz-Security-Token` query param should be omitted from the canonical signing
     * calculation. Normally, this parameter is added during signing if the credentials have a session token. The only
     * known case where this should be true is when signing a websocket handshake to IoT Core.
     *
     * If this value is false, a non-null security token is _still added to the request_ but it is not used in signature
     * calculation.
     */
    public val omitSessionToken: Boolean = builder.omitSessionToken

    /**
     * Determines the source of the canonical request's body public value. The default is
     * [HashSpecification.CalculateFromPayload], indicating that a public value will be calculated from the payload
     * during signing.
     */
    public val hashSpecification: HashSpecification =
        builder.hashSpecification ?: HashSpecification.CalculateFromPayload

    /**
     * Determines which body "hash" header, if any, should be added to the canonical request and the signed request.
     */
    public val signedBodyHeader: AwsSignedBodyHeader = builder.signedBodyHeader

    /**
     * The AWS credentials to sign with
     */
    public val credentials: Credentials = requireNotNull(builder.credentials) {
        "Signing config must specify credentials"
    }

    /**
     * Determines how long the signed request should be valid. If non-null and the signing transform is query param,
     * then signing will add `X-Amz-Expires` to the query string, equal to the public value specified here. If this
     * value is null or if header signing is being used then this parameter has no effect. Note that the resolution of
     * expiration is in seconds.
     */
    public val expiresAfter: Duration? = builder.expiresAfter

    public fun toBuilder(): Builder = Builder().also {
        it.region = region
        it.service = service
        it.signingDate = signingDate
        it.shouldSignHeader = shouldSignHeader
        it.algorithm = algorithm
        it.signatureType = signatureType
        it.useDoubleUriEncode = useDoubleUriEncode
        it.normalizeUriPath = normalizeUriPath
        it.omitSessionToken = omitSessionToken
        it.hashSpecification = hashSpecification
        it.signedBodyHeader = signedBodyHeader
        it.credentials = credentials
        it.expiresAfter = expiresAfter
    }

    public class Builder {
        public var region: String? = null
        public var service: String? = null
        public var signingDate: Instant? = null
        public var shouldSignHeader: ShouldSignHeaderPredicate = { _ -> true } // Don't reject any headers by default
        public var algorithm: AwsSigningAlgorithm = AwsSigningAlgorithm.SIGV4
        public var signatureType: AwsSignatureType = AwsSignatureType.HTTP_REQUEST_VIA_HEADERS
        public var useDoubleUriEncode: Boolean = true
        public var normalizeUriPath: Boolean = true
        public var omitSessionToken: Boolean = false
        public var hashSpecification: HashSpecification? = null
        public var signedBodyHeader: AwsSignedBodyHeader = AwsSignedBodyHeader.NONE
        public var credentials: Credentials? = null
        public var expiresAfter: Duration? = null

        public fun build(): AwsSigningConfig = AwsSigningConfig(this)
    }
}
