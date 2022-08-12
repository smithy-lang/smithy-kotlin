/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.client.ClientOption
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.AttributeKey

/**
 *  [ClientOption] instances related to signing.
 */
public object AwsSigningAttributes {
    /**
     * The signer implementation to use
     */
    public val Signer: ClientOption<AwsSigner> = ClientOption("Signer")

    /**
     * AWS region to be used for signing the request
     */
    public val SigningRegion: ClientOption<String> = ClientOption("AwsSigningRegion")

    /**
     * The signature version 4 service signing name to use in the credential scope when signing requests.
     * See: https://docs.aws.amazon.com/general/latest/gr/sigv4_elements.html
     */
    public val SigningService: ClientOption<String> = ClientOption("AwsSigningService")

    /**
     * Override the date to complete the signing process with. Defaults to current time when not specified.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val SigningDate: ClientOption<Instant> = ClientOption("SigningDate")

    /**
     * The [CredentialsProvider] to complete the signing process with. Defaults to the provider configured
     * on the service client.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val CredentialsProvider: ClientOption<CredentialsProvider> = ClientOption("CredentialsProvider")

    /**
     * The specification for determining the hash value for the request.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val HashSpecification: ClientOption<HashSpecification> = ClientOption("HashSpecification")

    /**
     * The signed body header type.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    public val SignedBodyHeader: ClientOption<AwsSignedBodyHeader> = ClientOption("SignedBodyHeader")

    /**
     * The signature of the HTTP request. This will only exist after the request has been signed.
     */
    public val RequestSignature: AttributeKey<ByteArray> = AttributeKey("AWS_HTTP_SIGNATURE")
}
