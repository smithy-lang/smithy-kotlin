/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.identity.Token
import aws.smithy.kotlin.runtime.net.isSecure

/**
 * [HttpSigner] that signs outgoing requests using the provided [Token] identity
 */
public class BearerTokenSigner : HttpSigner {
    override suspend fun sign(signingRequest: SignHttpRequest) {
        val identity = signingRequest.identity
        check(identity is Token) { "expected a ${Token::class} identity; found ${signingRequest.identity::class}" }

        // RFC 6750 § 5.2
        check(signingRequest.httpRequest.url.scheme.isSecure) { "https is required to use Bearer token auth" }

        val credentials = "Bearer ${identity.token}"
        signingRequest.httpRequest.headers["Authorization"] = credentials
    }
}
