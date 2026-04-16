/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeId

/**
 * HTTP auth scheme for HTTP Bearer authentication as defined in [RFC 6750](https://tools.ietf.org/html/rfc6750.html)
 */
public class BearerTokenAuthScheme : AuthScheme {
    override val schemeId: AuthSchemeId = AuthSchemeId.HttpBearer
    override val signer: HttpSigner = BearerTokenSigner()
}
