/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthSchemeId

/**
 * The user-accessible configuration properties for the SDKs HTTP authentication schemes
 */
public interface HttpAuthConfig {
    /**
     * New or overridden [AuthScheme]'s configured for this client. By default, the set
     * of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
     * precedence over the defaults and can be used to customize identity resolution and signing for specific
     * authentication schemes.
     */
    public val authSchemes: List<AuthScheme>

    /**
     * The ordered preference of [AuthScheme] that this client will use.
     */
    public val authSchemePreference: List<AuthSchemeId>?

    public interface Builder {
        /**
         * Register new or override default [AuthScheme]'s configured for this client. By default, the set
         * of auth schemes configured comes from the service model. An auth scheme configured explicitly takes
         * precedence over the defaults and can be used to customize identity resolution and signing for specific
         * authentication schemes.
         */
        public var authSchemes: List<AuthScheme>

        /**
         * The ordered preference of [AuthScheme] that this client will use.
         */
        public var authSchemePreference: List<AuthSchemeId>?
    }
}
