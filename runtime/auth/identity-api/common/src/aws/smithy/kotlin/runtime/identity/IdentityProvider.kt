/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.util.Attributes
import aws.smithy.kotlin.runtime.util.emptyAttributes

/**
 * Resolves identities for a service client
 */
public interface IdentityProvider {
    /**
     * Resolve the identity to authenticate requests with
     * @param attributes Additional attributes to feed into identity resolution. Typically, metadata from
     * selecting the authentication scheme.
     * @return An [Identity] that can be used to connect to the service
     */
    public suspend fun resolve(attributes: Attributes = emptyAttributes()): Identity
}
