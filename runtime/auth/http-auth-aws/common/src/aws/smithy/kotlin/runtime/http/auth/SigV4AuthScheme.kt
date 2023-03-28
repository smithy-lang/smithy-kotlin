/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner

// TODO - is AwsHttpSigner.Config what we want to use to configure this scheme?
/**
 * HTTP auth scheme for AWS signature version 4
 */
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
