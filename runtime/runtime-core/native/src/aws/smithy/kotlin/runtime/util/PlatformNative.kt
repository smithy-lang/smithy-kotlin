/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

internal actual object SystemDefaultProvider : PlatformProvider {
    override fun getAllEnvVars(): Map<String, String> {
        TODO("Not yet implemented")
    }

    override fun getenv(key: String): String? {
        TODO("Not yet implemented")
    }

    override val filePathSeparator: String
        get() = TODO("Not yet implemented")

    override suspend fun readFileOrNull(path: String): ByteArray? {
        TODO("Not yet implemented")
    }

    override suspend fun writeFile(path: String, data: ByteArray) {
        TODO("Not yet implemented")
    }

    override suspend fun fileExists(path: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun osInfo(): OperatingSystem {
        TODO("Not yet implemented")
    }

    override val isJvm: Boolean
        get() = TODO("Not yet implemented")
    override val isAndroid: Boolean
        get() = TODO("Not yet implemented")
    override val isBrowser: Boolean
        get() = TODO("Not yet implemented")
    override val isNode: Boolean
        get() = TODO("Not yet implemented")
    override val isNative: Boolean
        get() = TODO("Not yet implemented")

    override fun getAllProperties(): Map<String, String> {
        TODO("Not yet implemented")
    }

    override fun getProperty(key: String): String? {
        TODO("Not yet implemented")
    }
}
