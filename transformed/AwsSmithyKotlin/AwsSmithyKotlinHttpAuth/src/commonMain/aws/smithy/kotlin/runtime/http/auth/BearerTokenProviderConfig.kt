/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

/**
 * The user-accessible configuration properties for configuring a [BearerTokenProvider].
 */
public interface BearerTokenProviderConfig {
    /**
     * The token provider to use for authenticating requests when using [BearerTokenAuthScheme].
     * NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
     * client will not close it when the client is closed.
     */
    public val bearerTokenProvider: BearerTokenProvider

    // FIXME - should this be nullable to allow for bearer auth to not be configured (e.g. multiple auth schemes supported)
    public interface Builder {
        /**
         * The token provider to use for authenticating requests when using [BearerTokenAuthScheme].
         * NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
         * client will not close it when the client is closed.
         */
        public var bearerTokenProvider: BearerTokenProvider?
    }
}
