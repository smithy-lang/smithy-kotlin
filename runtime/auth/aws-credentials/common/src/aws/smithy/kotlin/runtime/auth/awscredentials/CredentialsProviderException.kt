/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.auth.awscredentials

import aws.smithy.kotlin.runtime.ClientException

/**
 * The [CredentialsProvider] experienced an error during credentials resolution
 */
public class CredentialsProviderException(message: String, cause: Throwable? = null) : ClientException(message, cause)
