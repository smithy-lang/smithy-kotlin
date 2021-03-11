/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.util

import java.util.*

public actual object Platform {
    /**
     * Get an environment variable or null
     */
    actual fun getenv(key: String): String? = System.getenv()[key]

    actual val isJvm: Boolean = true
    actual val isAndroid: Boolean = isAndroid()
    actual val isBrowser: Boolean = false
    actual val isNode: Boolean = false
    actual val isNative: Boolean = false

    actual fun osInfo(): OperatingSystem = getOsInfo()
}

private fun isAndroid(): Boolean {
    return try {
        Class.forName("android.os.Build")
        true
    } catch (ex: ClassNotFoundException) {
        false
    }
}

private fun normalize(value: String): String {
    return value.toLowerCase(Locale.US).replace(Regex("[^a-z0-9+]"), "")
}

private fun getOsInfo(): OperatingSystem {
    val name = normalize(System.getProperty("os.name"))

    val family = when {
        isAndroid() -> OsFamily.Android
        name.contains("windows") -> OsFamily.Windows
        name.contains("linux") -> OsFamily.Linux
        name.contains("macosx") -> OsFamily.MacOs
        else -> OsFamily.Other
    }

    val version = runCatching {
        normalize(System.getProperty("os.version").replace(" ", "-"))
    }.getOrNull()

    return OperatingSystem(family, version)
}
