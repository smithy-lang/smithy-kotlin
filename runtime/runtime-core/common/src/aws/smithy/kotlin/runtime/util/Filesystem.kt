/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

/**
 * Abstraction over filesystem
 */
public interface Filesystem {

    /**
     * The delimiter of segments in a path. For example in Linux: /home/user/documents
     * or Windows: C:\Program Files\Notepad.EXE
     */
    public val filePathSeparator: String

    /**
     * Read the contents of a file as a [String] or return null on any error.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem.
     * @return contents of file or null if error (file does not exist, etc.)
     */
    public suspend fun readFileOrNull(path: String): ByteArray?

    /**
     * Write the contents of a file. File will be created if it doesn't exist. Existing files will be overwritten.
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @param data the file contents to write to disk
     */
    public suspend fun writeFile(path: String, data: ByteArray)

    /**
     * Check if a file exists at the [path].
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     */
    public fun fileExists(path: String): Boolean

    public companion object {
        /**
         * Construct a fake filesystem from a mapping of paths to contents
         */
        public fun fromMap(data: Map<String, ByteArray>, filePathSeparator: String = "/"): Filesystem =
            MapFilesystem(data.toMutableMap(), filePathSeparator)
    }
}

internal class MapFilesystem(
    private val memFs: MutableMap<String, ByteArray>,
    override val filePathSeparator: String,
) : Filesystem {
    override suspend fun readFileOrNull(path: String): ByteArray? = memFs[path]
    override suspend fun writeFile(path: String, data: ByteArray) {
        memFs[path] = data
    }
    override fun fileExists(path: String): Boolean = memFs[path] != null
}
