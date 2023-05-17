/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.client.endpoints

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.AttributeKey

/**
 * Static attribute key for AWS endpoint auth schemes.
 */
@InternalApi
public val AuthSchemesAttributeKey: AttributeKey<List<AuthScheme>> = AttributeKey("authSchemes")

/**
 * A set of signing constraints for an AWS endpoint.
 */
@InternalApi
public sealed class AuthScheme {
    @InternalApi
    public data class SigV4(
        public val signingName: String?,
        public val disableDoubleEncoding: Boolean,
        public val signingRegion: String?,
    ) : AuthScheme()

    @InternalApi
    public data class SigV4A(
        public val signingName: String?,
        public val disableDoubleEncoding: Boolean,
        public val signingRegionSet: List<String>,
    ) : AuthScheme()
}

/**
 * Sugar extension to pull an auth scheme out of the attribute set.
 *
 * FUTURE: Right now we only support sigv4. The auth scheme property can include multiple schemes, for now we only pull
 * out the sigv4 one if present.
 */
@InternalApi
public val Endpoint.authScheme: AuthScheme.SigV4?
    get() = attributes.getOrNull(AuthSchemesAttributeKey)?.find { it is AuthScheme.SigV4 } as? AuthScheme.SigV4
