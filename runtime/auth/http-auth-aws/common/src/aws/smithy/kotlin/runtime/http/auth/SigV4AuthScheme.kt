/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeId

/**
 * HTTP auth scheme for AWS signature version 4
 */
public class SigV4AuthScheme(
    config: AwsHttpSigner.Config,
) : HttpAuthScheme {
    override val schemeId: AuthSchemeId = AuthSchemeId.AwsSigV4
    override val signer: AwsHttpSigner = AwsHttpSigner(config)
}
