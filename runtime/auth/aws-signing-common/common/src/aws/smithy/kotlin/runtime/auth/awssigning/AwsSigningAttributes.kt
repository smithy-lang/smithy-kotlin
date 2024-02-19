/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.collections.AttributeKey
import aws.smithy.kotlin.runtime.time.Instant
import kotlinx.coroutines.CompletableDeferred

/**
 *  [AttributeKey] instances related to signing.
 */
public object AwsSigningAttributes {
    /**
     * The signer implementation to use
     */
    public val Signer: AttributeKey<AwsSigner> = AttributeKey("aws.smithy.kotlin.signing#Signer")

    /**
     * AWS region to be used for signing the request
     */
    public val SigningRegion: AttributeKey<String> = AttributeKey("aws.smithy.kotlin.signing#AwsSigningRegion")

    /**
     * The AWS region-set used for computing the signature.
     */
    public val SigningRegionSet: AttributeKey<Set<String>> = AttributeKey("aws.smithy.kotlin.signing#AwsSigningRegionSet")

    /**
     * The signature version 4 service signing name to use in the credential scope when signing requests.
     * See: https://docs.aws.amazon.com/general/latest/gr/sigv4_elements.html
     */
    public val SigningService: AttributeKey<String> = AttributeKey("aws.smithy.kotlin.signing#AwsSigningService")

    /**
     * Override the date to complete the signing process with. Defaults to current time when not specified.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val SigningDate: AttributeKey<Instant> = AttributeKey("aws.smithy.kotlin.signing#SigningDate")

    /**
     * The [CredentialsProvider] to complete the signing process with. Defaults to the provider configured
     * on the service client.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val CredentialsProvider: AttributeKey<CredentialsProvider> = AttributeKey("aws.smithy.kotlin.signing#CredentialsProvider")

    /**
     * The specification for determining the hash value for the request.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val HashSpecification: AttributeKey<HashSpecification> = AttributeKey("aws.smithy.kotlin.signing#HashSpecification")

    /**
     * The signed body header type.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val SignedBodyHeader: AttributeKey<AwsSignedBodyHeader> = AttributeKey("aws.smithy.kotlin.signing#SignedBodyHeader")

    /**
     * The signature of the HTTP request. The signer will complete this once the request has been signed.
     * Operation middleware is responsible for resetting the completable deferred value.
     */
    public val RequestSignature: AttributeKey<CompletableDeferred<ByteArray>> = AttributeKey("aws.smithy.kotlin.signing#RequestSignature")

    /**
     * Flag indicating whether to enable double URI encoding. See [AwsSigningConfig.useDoubleUriEncode] for more details.
     */
    public val UseDoubleUriEncode: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin.signing#UseDoubleUriEncode")

    /**
     * Flag indicating whether to normalize the URI path. See [AwsSigningConfig.normalizeUriPath] for more details.
     */
    public val NormalizeUriPath: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin.signing#NormalizeUriPath")

    /**
     * Flag indicating whether to enable sending requests with `aws-chunked` content encoding. Defaults to `false`.
     * Note: This flag does not solely control aws-chunked behavior. The size of the request body must also be above a
     * defined threshold in order to be chunked.
     * @see <a href="https://docs.aws.amazon.com/AmazonS3/latest/API/sigv4-streaming.html">SigV4 Streaming</a>
     */
    public val EnableAwsChunked: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin.signing#EnableAwsChunked")

    /**
     * Flag indicating whether the X-Amz-Security-Token header should be omitted from the canonical request during signing.
     */
    public val OmitSessionToken: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin.signing#OmitSessionToken")
}
