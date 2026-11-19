/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.PlannedRemoval

internal actual object SystemDefaultProvider : PlatformProvider {
    actual override fun osInfo(): OperatingSystem = TODO("Not yet implemented on wasmJs: environment")

    actual override val filePathSeparator: String
        get() = TODO("Not yet implemented on wasmJs: filesystem")

    @OptIn(PlannedRemoval::class)
    @Deprecated("Use pathExists() instead", replaceWith = ReplaceWith("pathExists(path)"))
    actual override fun fileExists(path: String): Boolean = TODO("Not yet implemented on wasmJs: filesystem")

    @OptIn(PlannedRemoval::class)
    @Deprecated(
        "Blocks the calling thread. Use writeFile() instead",
        replaceWith = ReplaceWith("writeFile(path, data, writeType, mustExist, permissions)"),
    )
    actual override fun write(
        path: String,
        data: ByteArray,
        writeType: WriteType,
        mustExist: Boolean,
        permissions: String?,
    ): Unit = TODO("Not yet implemented on wasmJs: filesystem")

    actual override fun getAllEnvVars(): Map<String, String> = TODO("Not yet implemented on wasmJs: environment")
    actual override fun getAllProperties(): Map<String, String> = TODO("Not yet implemented on wasmJs: environment")
    actual override fun getProperty(key: String): String? = TODO("Not yet implemented on wasmJs: environment")
    actual override fun getenv(key: String): String? = TODO("Not yet implemented on wasmJs: environment")

    actual override suspend fun readFileOrNull(path: String): ByteArray? = TODO("Not yet implemented on wasmJs: filesystem")

    actual override val isJvm: Boolean = false
    actual override val isAndroid: Boolean = false
    actual override val isNative: Boolean = false

    actual override val isBrowser: Boolean
        get() = TODO("Not yet implemented on wasmJs: environment")

    actual override val isNode: Boolean
        get() = TODO("Not yet implemented on wasmJs: environment")
}
