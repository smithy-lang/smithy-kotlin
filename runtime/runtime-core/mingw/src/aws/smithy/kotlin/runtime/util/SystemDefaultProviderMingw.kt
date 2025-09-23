/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.sdk.kotlin.crt.util.osVersionFromKernel
import kotlinx.cinterop.*
import platform.posix.environ

public actual object SystemDefaultProvider : SystemDefaultProviderBase() {
    actual override val filePathSeparator: String = "\\"

    // FIXME We currently get the OS info by parsing from Kernel32.dll. Is there a less hacky way we can do this?
    actual override fun osInfo(): OperatingSystem = OperatingSystem(OsFamily.Windows, osVersionFromKernel())

    actual override fun getAllEnvVars(): Map<String, String> = memScoped {
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
