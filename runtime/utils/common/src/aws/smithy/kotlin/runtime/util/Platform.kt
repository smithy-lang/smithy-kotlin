/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

@InternalApi
public expect object Platform {
    /**
     * Get an environment variable or null
     *
     * @param key name of environment variable
     * @return value of environment variable or null if undefined
     */
    fun getenv(key: String): String?

    /**
     * Get a system property (on supported platforms)
     *
     * @param key name of environment variable
     * @return value of system property or null if undefined or platform does not support properties
     */
    fun getProperty(key: String): String?

    val isJvm: Boolean
    val isAndroid: Boolean
    val isBrowser: Boolean
    val isNode: Boolean
    val isNative: Boolean

    /**
     * The delimiter of segments in a path. For example in Linux: /home/user/documents
     * or Windows: C:\Program Files\Notepad.EXE
     */
    val filePathSeparator: String

    fun osInfo(): OperatingSystem

    /**
     * Read the contents of a file as a [String] or return null on any error.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem.
     * @return contents of file or null if error (file does not exist, etc.)
     */
    suspend fun readFileOrNull(path: String): ByteArray?
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
