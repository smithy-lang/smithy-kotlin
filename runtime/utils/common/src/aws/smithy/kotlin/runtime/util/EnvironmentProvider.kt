/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

/**
 * Provide a mapping from key to value
 */
public fun interface EnvironmentProvider {
    /**
     * Get an environment variable or null
     *
     * @param key name of environment variable
     * @return value of environment variable or null if undefined
     */
    public fun getenv(key: String): String?
}
