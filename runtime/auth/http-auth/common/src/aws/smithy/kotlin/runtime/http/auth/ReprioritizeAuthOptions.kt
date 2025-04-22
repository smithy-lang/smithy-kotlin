/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId

/**
 * Re-prioritize a resolved list of auth options based on a user's preference list
 */
@InternalApi
public fun reprioritizeAuthOptions(authSchemePreference: List<AuthSchemeId>, authOptions: List<AuthOption>): List<AuthOption> {
    // add preferred candidates first
    val preferredAuthOptions = authSchemePreference.mapNotNull { preferredSchemeId ->
        val preferredSchemeName = preferredSchemeId.id.substringAfter('#')
        authOptions.singleOrNull {
            it.schemeId.id.substringAfter('#') == preferredSchemeName
        }
    }

    val nonPreferredAuthOptions = authOptions.filterNot { it in preferredAuthOptions }

    return preferredAuthOptions + nonPreferredAuthOptions
}