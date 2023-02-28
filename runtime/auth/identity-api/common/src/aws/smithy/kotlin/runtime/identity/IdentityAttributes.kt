/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.util.AttributeKey

/**
 * Common [Identity] attribute keys
 */
public object IdentityAttributes {
    /**
     * The name of the [IdentityProvider]
     */
    public val ProviderName: AttributeKey<String> = AttributeKey("aws.smithy.kotlin#IdentityProviderName")
}

/**
 * The name of the [IdentityProvider] that sourced this [Identity]
 */
public val Identity.providerName: String?
    get() = attributes.getOrNull(IdentityAttributes.ProviderName)
