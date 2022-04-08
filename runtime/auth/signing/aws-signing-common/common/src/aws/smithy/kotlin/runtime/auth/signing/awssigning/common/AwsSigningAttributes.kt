/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.signing.awssigning.common

import aws.smithy.kotlin.runtime.auth.credentials.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.client.ClientOption
import aws.smithy.kotlin.runtime.time.Instant
import aws.smithy.kotlin.runtime.util.AttributeKey

/**
 *  [ClientOption] instances related to signing.
 */
object AwsSigningAttributes {
    /**
     * The signer implementation to use
     */
    val Signer: ClientOption<AwsSigner> = ClientOption("Signer")

    /**
     * AWS region to be used for signing the request
     */
    val SigningRegion: ClientOption<String> = ClientOption("AwsSigningRegion")

    /**
     * The signature version 4 service signing name to use in the credential scope when signing requests.
     * See: https://docs.aws.amazon.com/general/latest/gr/sigv4_elements.html
     */
    val SigningService: ClientOption<String> = ClientOption("AwsSigningService")

    /**
     * Mark a request payload as unsigned
     * See: https://awslabs.github.io/smithy/1.0/spec/aws/aws-auth.html#aws-auth-unsignedpayload-trait
     */
    val UnsignedPayload: ClientOption<Boolean> = ClientOption("UnsignedPayload")

    /**
     * Override the date to complete the signing process with. Defaults to current time when not specified.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    val SigningDate: ClientOption<Instant> = ClientOption("SigningDate")

    /**
     * The [CredentialsProvider] to complete the signing process with. Defaults to the provider configured
     * on the service client.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    val CredentialsProvider: ClientOption<CredentialsProvider> = ClientOption("CredentialsProvider")

    /**
     * The source for the body hash.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    val BodyHashSource: ClientOption<BodyHashSource> = ClientOption("BodyHashSource")

    /**
     * The signed body header type.
     *
     * **Note**: This is an advanced configuration option that does not normally need to be set manually.
     */
    val SignedBodyHeader: ClientOption<AwsSignedBodyHeader> = ClientOption("SignedBodyHeader")

    /**
     * The signature of the HTTP request. This will only exist after the request has been signed.
     */
    val RequestSignature: AttributeKey<ByteArray> = AttributeKey("AWS_HTTP_SIGNATURE")
}
