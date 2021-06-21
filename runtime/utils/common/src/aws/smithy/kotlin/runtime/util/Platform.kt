/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

@InternalApi
public expect object Platform {
    /**
     * Get an environment variable or null
     */
    fun getenv(key: String): String?

    val isJvm: Boolean
    val isAndroid: Boolean
    val isBrowser: Boolean
    val isNode: Boolean
    val isNative: Boolean

    fun osInfo(): OperatingSystem
}

data class OperatingSystem(val family: OsFamily, val version: String?)

public enum class OsFamily {
    Linux,
    MacOs,
    Windows,
    Android,
    Ios,
    Unknown;

    override fun toString(): String = when (this) {
        Linux -> "linux"
        MacOs -> "macos"
        Windows -> "windows"
        Android -> "android"
        Ios -> "ios"
        Unknown -> "unknown"
    }
}
