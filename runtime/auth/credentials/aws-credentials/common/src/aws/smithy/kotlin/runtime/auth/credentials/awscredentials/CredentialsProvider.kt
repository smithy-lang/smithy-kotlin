/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.auth.credentials.awscredentials

/**
 * Represents a producer/source of AWS credentials
 */
public interface CredentialsProvider {

    /**
     * Request credentials from the provider
     */
    public suspend fun getCredentials(): Credentials
}
