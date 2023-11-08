/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAttributes
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.util.*

/**
 * HTTP auth scheme for AWS signature version 4
 */
@InternalApi
public class SigV4AuthScheme(
    config: AwsHttpSigner.Config,
) : AuthScheme {
    public constructor(awsSigner: AwsSigner, serviceName: String? = null) : this(
        AwsHttpSigner.Config().apply {
            signer = awsSigner
            service = serviceName
        },
    )

    override val schemeId: AuthSchemeId = AuthSchemeId.AwsSigV4
    override val signer: AwsHttpSigner = AwsHttpSigner(config)
}

/**
 * Create a new [AuthOption] for the [SigV4AuthScheme]
 * @param unsignedPayload set the signing attribute to indicate the signer should use unsigned payload.
 * @param serviceName override the service name to sign for
 * @param signingRegion override the signing region to sign for
 * @param disableDoubleUriEncode disable double URI encoding
 * @return auth scheme option representing the [SigV4AuthScheme]
 */
@InternalApi
public fun sigV4(
    unsignedPayload: Boolean = false,
    serviceName: String? = null,
    signingRegion: String? = null,
    disableDoubleUriEncode: Boolean? = null,
): AuthOption {
    val attrs = if (unsignedPayload || serviceName != null || signingRegion != null || disableDoubleUriEncode != null) {
        val mutAttrs = mutableAttributes()

        if (unsignedPayload) {
            mutAttrs[AwsSigningAttributes.HashSpecification] = HashSpecification.UnsignedPayload
        }
        mutAttrs.setNotBlank(AwsSigningAttributes.SigningRegion, signingRegion)
        mutAttrs.setNotBlank(AwsSigningAttributes.SigningService, serviceName)
        if (disableDoubleUriEncode != null) {
            mutAttrs[AwsSigningAttributes.UseDoubleUriEncode] = !disableDoubleUriEncode
        }

        mutAttrs
    } else {
        emptyAttributes()
    }
    return AuthOption(AuthSchemeId.AwsSigV4, attrs)
}

internal fun MutableAttributes.setNotBlank(key: AttributeKey<String>, value: String?) {
    if (!value.isNullOrBlank()) set(key, value)
}
