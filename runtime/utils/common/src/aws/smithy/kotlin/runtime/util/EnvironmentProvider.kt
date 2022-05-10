/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

/**
 * Provide a mapping from key to value
 */
public interface EnvironmentProvider {
    /**
     * Get a map of all environment variables.
     * @return A map of string keys to string values.
     */
    public fun getAllEnvVars(): Map<String, String>

    /**
     * Get an environment variable or null
     *
     * @param key name of environment variable
     * @return value of environment variable or null if undefined
     */
    public fun getenv(key: String): String?
}
