/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.util.Uuid
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

/**
 * Cleanup behavior for temporary directories created by [withTempDir].
 */
public enum class TempDirCleanupMode {
    /** Always delete the directory after the block completes, regardless of success or failure. */
    ALWAYS,

    /** Only delete the directory if the block completes successfully. On failure, preserve for debugging. */
    ON_SUCCESS,

    /** Never delete the directory. */
    NEVER,
}

/**
 * Creates a temporary directory, runs [block] with the directory [Path], and cleans up based on [cleanup] mode.
 *
 * This is a cross-platform replacement for JUnit's `@TempDir`.
 *
 * Example:
 * ```
 * @Test
 * fun testSomething() = runTest {
 *     withTempDir { dir ->
 *         val file = Path(dir, "test.txt")
 *         // ...
 *     }
 * }
 * ```
 */
@OptIn(InternalApi::class)
public suspend fun <T> withTempDir(cleanup: TempDirCleanupMode = TempDirCleanupMode.ON_SUCCESS, block: suspend (dir: Path) -> T): T {
    val dir = Path(SystemTemporaryDirectory, "test-${Uuid.random()}")
    SystemFileSystem.createDirectories(dir)
    try {
        val result = block(dir)
        if (cleanup != TempDirCleanupMode.NEVER) {
            dir.deleteRecursively()
        }
        return result
    } catch (e: Throwable) {
        if (cleanup == TempDirCleanupMode.ALWAYS) {
            dir.deleteRecursively()
        }
        throw e
    }
}

private fun Path.deleteRecursively() {
    if (!SystemFileSystem.exists(this)) return
    val metadata = SystemFileSystem.metadataOrNull(this)
    if (metadata?.isDirectory == true) {
        SystemFileSystem.list(this).forEach { child ->
            child.deleteRecursively()
        }
    }
    SystemFileSystem.delete(this)
}
