/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.auth.AuthSchemeId

/**
 * Identity providers configured for the SDK.
 */
public fun interface IdentityProviderConfig {
    /**
     * Retrieve an identity provider for the provided auth scheme ID.
     */
    public fun identityProviderForScheme(schemeId: AuthSchemeId): IdentityProvider
}
