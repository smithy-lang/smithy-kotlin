/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.io.Closeable

/**
 * Represents a producer/source of AWS credentials
 */
public interface CredentialsProvider : Closeable {
    /**
     * Request credentials from the provider
     */
    public suspend fun getCredentials(): Credentials

    /**
     * Shutdown and cleanup any resources
     */
    override fun close()
}
