/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAlgorithm
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAttributes
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.util.emptyAttributes
import aws.smithy.kotlin.runtime.util.mutableAttributes

/**
 * HTTP auth scheme for AWS signature version 4 Asymmetric
 */
@InternalApi
public class SigV4AsymmetricAuthScheme(
    config: AwsHttpSigner.Config,
) : AuthScheme {
    public constructor(
        awsSigner: AwsSigner,
        serviceName: String? = null,
    ) : this(
        AwsHttpSigner.Config().apply {
            signer = awsSigner
            service = serviceName
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC
        },
    )

    override val schemeId: AuthSchemeId = AuthSchemeId.AwsSigV4Asymmetric
    override val signer: AwsHttpSigner = AwsHttpSigner(config)
}

/**
 * Create a new [AuthOption] for the [SigV4AsymmetricAuthScheme]
 * @param unsignedPayload set the signing attribute to indicate the signer should use unsigned payload.
 * @param serviceName override the service name to sign for
 * @param signingRegionSet override the signing region set to sign for
 * @param disableDoubleUriEncode disable double URI encoding
 * @return auth scheme option representing the [SigV4AsymmetricAuthScheme]
 */
@InternalApi
public fun sigV4A(
    unsignedPayload: Boolean = false,
    serviceName: String? = null,
    signingRegionSet: List<String>? = null,
    disableDoubleUriEncode: Boolean? = null,
): AuthOption {
    val attrs = if (unsignedPayload || serviceName != null || signingRegionSet != null || disableDoubleUriEncode != null) {
        val mutAttrs = mutableAttributes()

        if (unsignedPayload) {
            mutAttrs[AwsSigningAttributes.HashSpecification] = HashSpecification.UnsignedPayload
        }

        if (!signingRegionSet.isNullOrEmpty()) {
            mutAttrs[AwsSigningAttributes.SigningRegionSet] = signingRegionSet.toSet()
        }

        mutAttrs.setNotBlank(AwsSigningAttributes.SigningService, serviceName)

        if (disableDoubleUriEncode != null) {
            mutAttrs[AwsSigningAttributes.UseDoubleUriEncode] = !disableDoubleUriEncode
        }

        mutAttrs
    } else {
        emptyAttributes()
    }
    return AuthOption(AuthSchemeId.AwsSigV4Asymmetric, attrs)
}
