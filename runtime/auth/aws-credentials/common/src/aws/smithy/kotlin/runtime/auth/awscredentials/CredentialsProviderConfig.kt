/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

/**
 * The user-accessible configuration properties for configuring a [CredentialsProvider].
 */
public interface CredentialsProviderConfig {
    /**
     * The AWS credentials provider to use for authenticating requests.
     * NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
     * client will not close it when the client is closed.
     */
    public val credentialsProvider: CredentialsProvider

    public interface Builder {
        /**
         * The AWS credentials provider to use for authenticating requests.
         * NOTE: The caller is responsible for managing the lifetime of the provider when set. The SDK
         * client will not close it when the client is closed.
         */
        public var credentialsProvider: CredentialsProvider?
    }
}
