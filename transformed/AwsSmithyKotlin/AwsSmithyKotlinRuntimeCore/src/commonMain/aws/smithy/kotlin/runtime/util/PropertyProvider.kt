/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

/**
 * Provide a mapping from (JVM) property key to value
 */
public interface PropertyProvider {
    /**
     * Get a map of all JVM system properties.
     * @return A map of string keys to string values.
     */
    public fun getAllProperties(): Map<String, String>

    /**
     * Get a system property (on supported platforms)
     *
     * @param key name of environment variable
     * @return value of system property or null if undefined or platform does not support properties
     */
    public fun getProperty(key: String): String?
}
