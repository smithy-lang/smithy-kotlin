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
public interface AuthOption {
    /**
     * The ID of the authentication scheme
     */
    public val schemeId: AuthSchemeId

    /**
     * Identity or signer attributes to use with this resolved authentication scheme
     */
    public val attributes: Attributes
}

public fun AuthOption(id: AuthSchemeId, attributes: Attributes = emptyAttributes()): AuthOption =
    AuthOptionImpl(id, attributes)

private data class AuthOptionImpl(
    override val schemeId: AuthSchemeId,
    override val attributes: Attributes,
) : AuthOption
