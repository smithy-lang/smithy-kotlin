/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth

/**
 * Resolves the candidate set of authentication schemes for an operation
 */
public interface AuthSchemeProvider<in T> {
    /**
     * Resolve the candidate set of authentication schemes for an operation
     * @param params The input context for the resolver function
     * @return a list of candidate [AuthSchemeOption] that can be used for an operation
     */
    public suspend fun resolveAuthScheme(params: T): List<AuthSchemeOption>
}
