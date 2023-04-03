/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.AuthSchemeOption
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningAttributes
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.util.attributesOf
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * HTTP auth scheme for AWS signature version 4
 */
@InternalApi
public class SigV4AuthScheme(
    config: AwsHttpSigner.Config,
) : HttpAuthScheme {
    public constructor(awsSigner: AwsSigner, serviceName: String) : this(
        AwsHttpSigner.Config().apply {
            signer = awsSigner
            service = serviceName
        },
    )

    override val schemeId: AuthSchemeId = AuthSchemeId.AwsSigV4
    override val signer: AwsHttpSigner = AwsHttpSigner(config)
}

/**
 * Create a new [AuthSchemeOption] for the [SigV4AuthScheme]
 * @param unsignedPayload set the signing attribute to indicate the signer should use unsigned payload.
 * @return auth scheme option representing the [SigV4AuthScheme]
 */
@InternalApi
public fun sigv4(unsignedPayload: Boolean = false): AuthSchemeOption {
    val attrs = if (unsignedPayload) {
        attributesOf {
            AwsSigningAttributes.HashSpecification to HashSpecification.UnsignedPayload
        }
    } else {
        emptyAttributes()
    }
    return AuthSchemeOption(AuthSchemeId.AwsSigV4, attrs)
}
