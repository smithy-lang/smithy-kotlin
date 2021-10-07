/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
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
    val filePathSeparator: String

    /**
     * Read the contents of a file as a [String] or return null on any error.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem.
     * @return contents of file or null if error (file does not exist, etc.)
     */
    public suspend fun readFileOrNull(path: String): ByteArray?

    companion object {
        /**
         * Construct a fake filesystem from a mapping of paths to contents
         */
        fun fromMap(data: Map<String, ByteArray>, filePathSeparator: String = "/"): Filesystem = MapFilesystem(data, filePathSeparator)
    }
}

internal class MapFilesystem(
    private val data: Map<String, ByteArray>,
    override val filePathSeparator: String
) : Filesystem {
    override suspend fun readFileOrNull(path: String): ByteArray? = data[path]
}
