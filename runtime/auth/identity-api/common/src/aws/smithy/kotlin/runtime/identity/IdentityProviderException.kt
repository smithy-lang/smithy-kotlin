/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.identity

import aws.smithy.kotlin.runtime.ClientException

/**
 * The [IdentityProvider] experienced an error during [Identity] resolution
 */
public class IdentityProviderException(message: String, cause: Throwable? = null) : ClientException(message, cause)
