/*
<<<<<<< HEAD
=======
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/*
>>>>>>> origin/main
* Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
* SPDX-License-Identifier: Apache-2.0
*/
package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.auth.awscredentials.CredentialsProvider
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig.Companion.invoke
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.time.Clock
import kotlin.time.Duration

/**
 * Generates an authentication token, which is a SigV4-signed URL with the HTTP scheme removed.
 * @param service The name of the service the token is being generated for
 * @param credentialsProvider The [CredentialsProvider] which will provide credentials to use when generating the auth token
 * @param signer The [AwsSigner] implementation to use when creating the authentication token
 * @param clock The [Clock] implementation to use
 */
public class AuthTokenGenerator(
    public val service: String,
    public val credentialsProvider: CredentialsProvider,
    public val signer: AwsSigner,
    public val clock: Clock = Clock.System,
) {
    private fun Url.trimScheme(): String = toString().removePrefix(scheme.protocolName).removePrefix("://")

    public suspend fun generateAuthToken(endpoint: Url, region: String, expiration: Duration): String {
        val req = HttpRequest(
            HttpMethod.GET,
            endpoint,
            headers = Headers {
                append("Host", endpoint.hostAndPort)
            },
        )

        val config = AwsSigningConfig {
            credentials = credentialsProvider.resolve()
            this.region = region
            service = this@AuthTokenGenerator.service
            signingDate = clock.now()
            expiresAfter = expiration
            signatureType = AwsSignatureType.HTTP_REQUEST_VIA_QUERY_PARAMS
        }

        return signer.sign(req, config).output.url.trimScheme()
    }
}
