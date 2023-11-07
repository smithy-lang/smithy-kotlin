/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAttributes
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.util.emptyAttributes
import aws.smithy.kotlin.runtime.util.mutableAttributes

/**
 * Create a new [AuthOption] for the [SigV4AsymetricAuthScheme]
 * @param unsignedPayload set the signing attribute to indicate the signer should use unsigned payload.
 * @param serviceName override the service name to sign for
 * @param signingRegionSet override the signing region set to sign for
 * @param disableDoubleUriEncode disable double URI encoding
 * @return auth scheme option representing the [SigV4AsymetricAuthScheme]
 */
@InternalApi
public fun sigv4A(
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
            mutAttrs[AwsSigningAttributes.EnableDoubleUriEncode] = !disableDoubleUriEncode
        }

        mutAttrs
    } else {
        emptyAttributes()
    }
    return AuthOption(AuthSchemeId.AwsSigV4Asymmetric, attrs)
}
