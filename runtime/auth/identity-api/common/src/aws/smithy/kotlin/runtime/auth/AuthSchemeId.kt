/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth
import kotlin.jvm.JvmInline

/**
 * Represents a unique authentication scheme ID
 */
@JvmInline
public value class AuthSchemeId(public val id: String) {
    public companion object {
        /**
         * Indicates that an operation MAY be invoked without authentication
         */
        public val Anonymous: AuthSchemeId = AuthSchemeId("smithy.api#noAuth")

        /**
         * HTTP Basic Authentication as defined in [RFC 2617](https://tools.ietf.org/html/rfc2617.html)
         */
        public val HttpBasic: AuthSchemeId = AuthSchemeId("smithy.api#httpBasicAuth")

        /**
         * HTTP Digest Authentication as defined in [RFC 2617](https://tools.ietf.org/html/rfc2617.html)
         */
        public val HttpDigest: AuthSchemeId = AuthSchemeId("smithy.api#httpDigestAuth")

        /**
         * HTTP Bearer Authentication as defined in [RFC 6750](https://tools.ietf.org/html/rfc6750.html)
         */
        public val HttpBearer: AuthSchemeId = AuthSchemeId("smithy.api#httpBearerAuth")

        /**
         * HTTP specific authentication using an API key sent in a header or query string parameter
         */
        public val HttpApiKey: AuthSchemeId = AuthSchemeId("smithy.api#httpApiKeyAuth")

        /**
         * AWS Signature Version 4 authentication
         */
        public val AwsSigV4: AuthSchemeId = AuthSchemeId("aws.auth#sigv4")

        /**
         * AWS Signature Version 4 asymmetric authentication
         */
        public val AwsSigV4Asymmetric: AuthSchemeId = AuthSchemeId("aws.auth#sigv4a")

        /**
         * AWS Signature Version 4 authentication for S3 Express One Zone
         */
        public val AwsSigV4S3Express: AuthSchemeId = AuthSchemeId("aws.auth#sigv4-s3express")
    }
}
