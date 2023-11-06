/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.client.endpoints.SigningContext
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.MutableAttributes
import aws.smithy.kotlin.runtime.util.setIfValueNotNull
import kotlinx.coroutines.CompletableDeferred

/**
 *  [AttributeKey] instances related to signing.
 */
public object AwsSigningAttributes {
    /**
     * The signer implementation to use
     */
    public val Signer: AttributeKey<AwsSigner> = AttributeKey("aws.smithy.kotlin#Signer")

    /**
     * AWS region to be used for signing the request
     */
    public val SigningRegion: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#AwsSigningRegion")

    /**
     *
     */
    public val SigningRegionSet: AttributeKey<Set<String>> = AttributeKey("aws.smithy.kotlin#AwsSigningRegionSet")

    /**
     * The signature version 4 service signing name to use in the credential scope when signing requests.
     * See: https://docs.aws.amazon.com/general/latest/gr/sigv4_elements.html
     */
    public val SigningService: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#AwsSigningService")

    /**
     * Override the date to complete the signing process with. Defaults to current time when not specified.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val SigningDate: AttributeKey<Instant> = AttributeKey("aws.smithy.kotlin#SigningDate")

    /**
     * The [CredentialsProvider] to complete the signing process with. Defaults to the provider configured
     * on the service client.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val CredentialsProvider: AttributeKey<CredentialsProvider> = AttributeKey("aws.smithy.kotlin#CredentialsProvider")

    /**
     * The specification for determining the hash value for the request.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val HashSpecification: AttributeKey<HashSpecification> = AttributeKey("aws.smithy.kotlin#HashSpecification")

    /**
     * The signed body header type.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val SignedBodyHeader: AttributeKey<AwsSignedBodyHeader> = AttributeKey("aws.smithy.kotlin#SignedBodyHeader")

    /**
     * The signature of the HTTP request. The signer will complete this once the request has been signed.
     * Operation middleware is responsible for resetting the completable deferred value.
     */
    public val RequestSignature: AttributeKey<CompletableDeferred<ByteArray>> = AttributeKey("aws.smithy.kotlin#RequestSignature")

    /**
     * Flag indicating whether to enable double URI encoding. See [AwsSigningConfig.useDoubleUriEncode] for more details.
     */
    public val EnableDoubleUriEncode: AttributeKey<Boolean> = AttributeKey("aws.smithy.kotlin#EnableDoubleUriEncode")
}

/**
 * Merges this signing context into the given [MutableAttributes].
 * @param attributes The attributes into which to merge the values from this signing context.
 */
@InternalApi
public fun SigningContext.mergeInto(attributes: MutableAttributes) {
    when (this) {
        is SigningContext.SigV4 -> {
            attributes.setUnlessBlank(AwsSigningAttributes.SigningService, signingName)
            attributes.setUnlessBlank(AwsSigningAttributes.SigningRegion, signingRegion)
            attributes.setIfValueNotNull(AwsSigningAttributes.EnableDoubleUriEncode, !disableDoubleEncoding)
        }
        is SigningContext.SigV4A -> {
            attributes.setUnlessBlank(AwsSigningAttributes.SigningService, signingName)
            attributes[AwsSigningAttributes.SigningRegionSet] = signingRegionSet.toSet()
            attributes.setIfValueNotNull(AwsSigningAttributes.EnableDoubleUriEncode, !disableDoubleEncoding)
        }
    }
}

private fun MutableAttributes.setUnlessBlank(key: AttributeKey<String>, value: String?) {
    if (!value.isNullOrBlank()) set(key, value)
}
