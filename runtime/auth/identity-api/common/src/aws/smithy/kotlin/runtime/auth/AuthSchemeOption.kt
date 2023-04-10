/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth

import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * A tuple of [AuthSchemeId] and typed properties. AuthSchemeOption represents a candidate
 * authentication scheme.
 */
public data class AuthSchemeOption(
    /**
     * The ID of the authentication scheme
     */
    public val schemeId: AuthSchemeId,

    /**
     * Identity or signer attributes to use with this resolved authentication scheme
     */
    public val attributes: Attributes = emptyAttributes(),
)
