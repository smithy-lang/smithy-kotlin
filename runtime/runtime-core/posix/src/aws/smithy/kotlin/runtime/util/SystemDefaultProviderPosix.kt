/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.cinterop.*
import platform.posix.*

internal actual val rawEnvironmentVariables: CPointer<CPointerVarOf<CPointer<ByteVarOf<Byte>>>>? = __environ

public actual object object SystemDefaultProvider : PlatformProvider {
    actual override val filePathSeparator: String = "/"

    actual override fun osInfo(): OperatingSystem = memScoped {
        val utsname = alloc<utsname>()
        uname(utsname.ptr)

        val sysName = utsname.sysname.toKString().lowercase()
        val version = utsname.release.toKString()
        val machine = utsname.machine.toKString().lowercase() // Helps differentiate Apple platforms

        val family = when {
            sysName.contains("darwin") -> {
                when {
                    machine.startsWith("iphone") -> OsFamily.Ios
                    // TODO Validate that iPadOS/tvOS/watchOS resolves correctly on each of these devices
                    machine.startsWith("ipad") -> OsFamily.IpadOs
                    machine.startsWith("tv") -> OsFamily.TvOs
                    machine.startsWith("watch") -> OsFamily.WatchOs
                    else -> OsFamily.MacOs
                }
            }
            sysName.contains("linux") -> OsFamily.Linux
            else -> OsFamily.Unknown
        }

        return OperatingSystem(family, version)
    }
}
