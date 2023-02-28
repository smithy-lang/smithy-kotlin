/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

/**
 * Resolves identities for a service client
 */
public interface IdentityProvider {
    /**
     * Resolve the identity to authenticate requests with
     * @return an [Identity] that can be used to connect to the service
     */
    public suspend fun resolveIdentity(): Identity
}