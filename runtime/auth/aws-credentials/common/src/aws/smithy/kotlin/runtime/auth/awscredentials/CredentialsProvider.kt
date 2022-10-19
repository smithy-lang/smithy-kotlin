/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

/**
 * Represents a producer/source of AWS credentials
 */
public interface CredentialsProvider {
    /**
     * Request credentials from the provider
     * @return A set of [Credentials].
     */
    public suspend fun getCredentials(): Credentials
}
