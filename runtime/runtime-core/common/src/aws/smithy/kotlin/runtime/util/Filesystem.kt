/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import okio.FileSystem as OkioFileSystem

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
    // TODO: Deprecate
    // @Deprecated("Use read() instead", replaceWith = ReplaceWith("read(path, readAll = true)"))
    public suspend fun readFileOrNull(path: String): ByteArray?

    /**
     * Write the contents of a file. File will be created if it doesn't exist. Existing files will be overwritten.
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @param data the file contents to write to disk
     */
    // TODO: Deprecate
    // @Deprecated("Use write() instead", replaceWith = ReplaceWith("write(path, data, WriteType.OVERWRITE)"))
    public suspend fun writeFile(path: String, data: ByteArray)

    /**
     * Check if a file exists at the [path].
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     */
    // TODO: Deprecate
    // @Deprecated("Use kotlinx.io.files.SystemFileSystem.exists(Path(path)) instead")
    public fun fileExists(path: String): Boolean

    /**
     * Write [data] to a file at [path] using the specified [writeType] strategy.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @param data the bytes to write
     * @param writeType the write strategy: [WriteType.OVERWRITE] to replace file contents,
     * [WriteType.APPEND] to add to the end, or [WriteType.OFFSET] to write at a specific byte position
     * @param mustExist if true, throws [aws.smithy.kotlin.runtime.io.IOException] when the file does not exist
     */
    public fun write(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean = false)

    /**
     * Atomically move a file from [source] to [destination].
     *
     * @param source fully qualified path of the file to move
     * @param destination fully qualified path of the target location
     * @param mustExist if true, throws [FileNotFoundException] when [source] does not exist
     * @param overwrite if false, throws [aws.smithy.kotlin.runtime.io.IOException] when [destination] already exists
     */
    public fun atomicMove(source: String, destination: String, mustExist: Boolean = true, overwrite: Boolean = false) {
        val sourcePath = Path(source)
        val destinationPath = Path(destination)

        if (!SystemFileSystem.exists(sourcePath) && mustExist) {
            throw FileNotFoundException("$sourcePath does not exist and mustExist is set to true")
        }

        if (SystemFileSystem.exists(destinationPath) && !overwrite) {
            throw IOException("$destinationPath already exists and overwrite is set to false")
        }

        SystemFileSystem.atomicMove(sourcePath, destinationPath)
    }

    /**
     * Delete a file at [path].
     *
     * @param path fully qualified path of the file to delete
     * @param mustExist if true, throws when the file does not exist
     */
    public fun delete(path: String, mustExist: Boolean = true): Unit = SystemFileSystem.delete(Path(path), mustExist)

    /**
     * List the names of files and directories within the directory at [path].
     *
     * @param path fully qualified path of the directory to list
     * @param mustExist if true, throws [FileNotFoundException] when [path] does not exist
     * @return collection of entry names in the directory
     */
    public fun list(path: String, mustExist: Boolean = true): Collection<String> {
        val path = Path(path)
        if (!SystemFileSystem.exists(path) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }
        return SystemFileSystem.list(path).map { it.name }
    }

    /**
     * Create a directory (and any missing parent directories) at [path].
     *
     * @param path fully qualified path of the directory to create
     * @param mustCreate if true, throws when the directory already exists
     */
    public fun createDir(path: String, mustCreate: Boolean = false): Unit = SystemFileSystem.createDirectories(Path(path), mustCreate)

    /**
     * Get the size of a file at [path] in bytes.
     *
     * @param path fully qualified path of the file
     * @param mustExist if true, throws [FileNotFoundException] when [path] does not exist
     * @return the file size in bytes
     * @throws [aws.smithy.kotlin.runtime.io.IOException] if the size cannot be determined
     */
    public fun size(path: String, mustExist: Boolean = true): Long {
        val path = Path(path)
        if (!SystemFileSystem.exists(path) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }

        return SystemFileSystem.metadataOrNull(path)?.size ?: throw IOException("Unable to find size for $path, found null")
    }

    /**
     * Read bytes from a file at [path].
     *
     * @param path fully qualified path of the file to read
     * @param amount number of bytes to read (ignored when [readAll] is true)
     * @param offset byte offset to start reading from (ignored when [readAll] is true)
     * @param readAll if true, reads the entire file contents
     * @param mustExist if true, throws [FileNotFoundException] when [path] does not exist
     * @return the bytes read from the file
     */
    public fun read(path: String, amount: Long = 0, offset: Long = 0, readAll: Boolean = false, mustExist: Boolean = true): ByteArray {
        if (!SystemFileSystem.exists(Path(path)) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }

        val path = path.toPath()
        return OkioFileSystem.SYSTEM.source(path).buffer().use {
            if (readAll) {
                it.readByteArray()
            } else {
                it.skip(offset)
                it.readByteArray(amount)
            }
        }
    }

    public companion object {
        /**
         * Construct a fake filesystem from a mapping of paths to contents
         */
        public fun fromMap(data: Map<String, ByteArray>, filePathSeparator: String = "/"): Filesystem = MapFilesystem(data.toMutableMap(), filePathSeparator)
    }
}

/**
 * Strategy for writing data to a file.
 */
public sealed class WriteType {
    /**
     * Append data to the end of the file.
     */
    public object APPEND : WriteType()

    /**
     * Overwrite the file contents, truncating any existing data.
     */
    public object OVERWRITE : WriteType()

    /**
     * Write data at a specific byte [offset] within the file.
     */
    public data class OFFSET(public val offset: Long) : WriteType()
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
    override fun write(
        path: String,
        data: ByteArray,
        writeType: WriteType,
        mustExist: Boolean,
    ) {
        if (memFs[path] == null && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }
        when (writeType) {
            is WriteType.OFFSET -> {
                val existing = memFs[path] ?: ByteArray(0)
                val end = (writeType.offset.toInt() + data.size)
                val result = existing.copyOf(newSize = maxOf(existing.size, end))
                data.copyInto(result, destinationOffset = writeType.offset.toInt())
                memFs[path] = result
            }
            is WriteType.APPEND -> memFs[path] = memFs[path]?.let { it + data } ?: data
            is WriteType.OVERWRITE -> memFs[path] = data
        }
    }
}
