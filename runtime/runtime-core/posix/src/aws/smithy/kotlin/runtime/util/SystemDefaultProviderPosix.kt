/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.cinterop.*
import aws.smithy.platform.posix.get_environ_ptr
import platform.posix.uname
import platform.posix.utsname

public actual object SystemDefaultProvider : SystemDefaultProviderBase() {
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

    actual override fun getAllEnvVars(): Map<String, String> = memScoped {
        val environ = get_environ_ptr()
        generateSequence(0) { it + 1 }
            .map { idx -> environ?.get(idx)?.toKString() }
            .takeWhile { it != null }
            .associate { env ->
                val parts = env?.split("=", limit = 2)
                check(parts?.size == 2) { "Environment entry \"$env\" is malformed" }
                parts[0] to parts[1]
            }
    }
}
