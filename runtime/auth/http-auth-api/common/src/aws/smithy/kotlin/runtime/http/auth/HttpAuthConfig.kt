/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

/**
 * The user-accessible configuration properties for the SDKs HTTP authentication schemes
 */
public interface HttpAuthConfig {
    /**
     * New or overridden [HttpAuthScheme]'s configured for this client. By default, the set
     * of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
     * precedence over the defaults and can be used to customize identity resolution and signing for specific
     * authentication schemes.
     */
    public val authSchemes: List<HttpAuthScheme>

    public interface Builder {
        /**
         * Register new or override default [HttpAuthScheme]'s configured for this client. By default, the set
         * of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
         * precedence over the defaults and can be used to customize identity resolution and signing for specific
         * authentication schemes.
         */
        public var authSchemes: List<HttpAuthScheme>
    }
}
