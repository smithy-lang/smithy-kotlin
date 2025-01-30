/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi

public interface PlatformEnvironProvider :
    EnvironmentProvider,
    PropertyProvider
public interface PlatformProvider :
    PlatformEnvironProvider,
    Filesystem {
    public companion object {
        /**
         * System default implementation of [PlatformProvider]
         */
        @InternalApi
        public val System: PlatformProvider = SystemDefaultProvider
    }

    /**
     * Get the operating system info
     */
    public fun osInfo(): OperatingSystem

    public val isJvm: Boolean
    public val isAndroid: Boolean
    public val isBrowser: Boolean
    public val isNode: Boolean
    public val isNative: Boolean
}

internal expect object SystemDefaultProvider : PlatformProvider {
    override fun osInfo(): OperatingSystem
    override val filePathSeparator: String
    override fun fileExists(path: String): Boolean
    override fun getAllEnvVars(): Map<String, String>
    override fun getAllProperties(): Map<String, String>
    override fun getProperty(key: String): String?
    override fun getenv(key: String): String?
    override suspend fun readFileOrNull(path: String): ByteArray?
    override suspend fun writeFile(path: String, data: ByteArray)
    override suspend fun deleteFile(path: String)
    override val isJvm: Boolean
    override val isAndroid: Boolean
    override val isBrowser: Boolean
    override val isNode: Boolean
    override val isNative: Boolean
}

public data class OperatingSystem(val family: OsFamily, val version: String?)

public enum class OsFamily {
    Linux,
    MacOs,
    Windows,
    Android,
    Ios,
    Unknown,
    ;

    override fun toString(): String = when (this) {
        Linux -> "linux"
        MacOs -> "macos"
        Windows -> "windows"
        Android -> "android"
        Ios -> "ios"
        Unknown -> "unknown"
    }
}
