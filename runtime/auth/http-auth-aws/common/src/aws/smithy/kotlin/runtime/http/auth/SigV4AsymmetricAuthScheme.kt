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
import aws.smithy.kotlin.runtime.collections.emptyAttributes
import aws.smithy.kotlin.runtime.collections.mutableAttributes
import aws.smithy.kotlin.runtime.identity.IdentityProvider
import aws.smithy.kotlin.runtime.identity.IdentityProviderConfig

/**
 * HTTP auth scheme for AWS signature version 4 Asymmetric
 */
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

    public constructor(
        awsSigner: AwsSigner,
        serviceName: String? = null,
        clientSigningRegionSet: List<String>? = null,
    ) : this (
        AwsHttpSigner.Config().apply {
            signer = awsSigner
            service = serviceName
            algorithm = AwsSigningAlgorithm.SIGV4_ASYMMETRIC
            clientSigv4aSigningRegionSet = clientSigningRegionSet
        },
    )

    // FIXME - remove when we add full support for SigV4A in codegen
    override fun identityProvider(identityProviderConfig: IdentityProviderConfig): IdentityProvider =
        identityProviderConfig.identityProviderForScheme(AuthSchemeId.AwsSigV4Asymmetric)

    override val schemeId: AuthSchemeId = AuthSchemeId.AwsSigV4Asymmetric
    override val signer: AwsHttpSigner = AwsHttpSigner(config)
}

/**
 * Create a new [AuthOption] for the [SigV4AsymmetricAuthScheme]
 * @param unsignedPayload set the signing attribute to indicate the signer should use unsigned payload.
 * @param serviceName override the service name to sign for
 * @param signingRegionSet override the signing region set to sign for
 * @param disableDoubleUriEncode disable double URI encoding
 * @param normalizeUriPath flag indicating if the URI path should be normalized when forming the canonical request
 * @return auth scheme option representing the [SigV4AsymmetricAuthScheme]
 */
@InternalApi
public fun sigV4A(
    unsignedPayload: Boolean = false,
    serviceName: String? = null,
    signingRegionSet: List<String>? = null,
    disableDoubleUriEncode: Boolean? = null,
    normalizeUriPath: Boolean? = null,
): AuthOption {
    val attrs = if (unsignedPayload || serviceName != null || signingRegionSet != null || disableDoubleUriEncode != null || normalizeUriPath != null) {
        val mutAttrs = mutableAttributes()
        if (!signingRegionSet.isNullOrEmpty()) {
            mutAttrs[AwsSigningAttributes.SigningRegionSet] = signingRegionSet.toSet()
        }
        setCommonSigV4Attrs(mutAttrs, unsignedPayload, serviceName, disableDoubleUriEncode, normalizeUriPath)

        mutAttrs
    } else {
        emptyAttributes()
    }
    return AuthOption(AuthSchemeId.AwsSigV4Asymmetric, attrs)
}
