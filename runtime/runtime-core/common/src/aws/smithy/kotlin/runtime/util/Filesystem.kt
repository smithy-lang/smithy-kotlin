/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemPathSeparator
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use
import kotlin.jvm.JvmName
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
    // @Deprecated("Use exists() instead", replaceWith = ReplaceWith("exists(path)"))
    public fun fileExists(path: String): Boolean

    /**
     * Write [data] to a file at [path] using the specified [writeType] strategy.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @param data the bytes to write
     * @param writeType the write strategy: [WriteType.OVERWRITE] to replace file contents,
     * [WriteType.APPEND] to add to the end, or [WriteType.OFFSET] to write at a specific byte position
     * @param mustExist if true, throws [IOException] when the file does not exist
     * @param permissions optional POSIX file permissions as an octal string to apply when the file is created
     * (e.g., `"600"` for owner read/write only). Ignored if the file already exists or the platform does not
     * support POSIX permissions (e.g., Windows).
     */
    public fun write(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean = false, permissions: String? = null)

    /**
     * Atomically move a file from [source] to [destination].
     *
     * @param source fully qualified path of the file to move
     * @param destination fully qualified path of the target location
     * @param mustExist if true, throws [FileNotFoundException] when [source] does not exist
     * @param overwrite if false, throws [IOException] when [destination] already exists
     */
    public fun atomicMove(source: String, destination: String, mustExist: Boolean = true, overwrite: Boolean = false) {
        if (!exists(source) && mustExist) {
            throw FileNotFoundException("$source does not exist and mustExist is set to true")
        }

        if (exists(destination) && !overwrite) {
            throw IOException("$destination already exists and overwrite is set to false")
        }

        SystemFileSystem.atomicMove(Path(source), Path(destination))
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
        if (!exists(path) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }
        return SystemFileSystem.list(Path(path)).map { it.name }
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
     * @throws [IOException] if the size cannot be determined
     */
    public fun size(path: String, mustExist: Boolean = true): Long {
        if (!exists(path) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }

        return SystemFileSystem.metadataOrNull(Path(path))?.size ?: throw IOException("Unable to find size for $path, found null")
    }

    /**
     * Read bytes from a file at [path].
     *
     * @param path fully qualified path of the file to read
     * @param amount number of bytes to read (ignored when [readAll] is true)
     * @param offset byte offset to start reading from (ignored when [readAll] is true)
     * @param readAll if true, reads the entire file contents
     * @return the bytes read from the file
     * @throws FileNotFoundException if the file does not exist
     */
    public fun read(path: String, amount: Long = 0, offset: Long = 0, readAll: Boolean = false): ByteArray {
        if (!exists(path)) {
            throw FileNotFoundException("$path does not exist")
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

    /**
     * Read bytes from a file at [path], returning null if the file does not exist and [mustExist] is false.
     *
     * @param path fully qualified path of the file to read
     * @param amount number of bytes to read (ignored when [readAll] is true)
     * @param offset byte offset to start reading from (ignored when [readAll] is true)
     * @param readAll if true, reads the entire file contents
     * @param mustExist if true, throws [FileNotFoundException] when [path] does not exist; if false, returns null
     * @return the bytes read from the file, or null if the file does not exist and [mustExist] is false
     */
    public fun readOrNull(path: String, amount: Long = 0, offset: Long = 0, readAll: Boolean = false, mustExist: Boolean = true): ByteArray? {
        if (!exists(path)) {
            if (mustExist) {
                throw FileNotFoundException("$path does not exist and mustExist is set to true")
            } else {
                return null
            }
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

    /**
     * Check if a file or directory exists at [path].
     *
     * @param path fully qualified path
     * @return true if a file or directory exists at [path], false otherwise
     */
    public fun exists(path: String): Boolean = SystemFileSystem.exists(Path(path))

    public companion object {
        /**
         * Construct a fake filesystem from a mapping of paths to contents
         */
        // TODO deprecate
        public fun fromMap(data: Map<String, ByteArray>, filePathSeparator: String = "/"): Filesystem = MapFilesystem(
            TestFile.transformMap(data).toMutableMap(),
            filePathSeparator,
        )

        /**
         * Construct a fake filesystem from a mapping of paths to contents
         */
        @JvmName("fromMapStringTestFile")
        public fun fromMap(data: Map<String, TestFile>, filePathSeparator: String = "/"): Filesystem = MapFilesystem(
            data.toMutableMap(),
            filePathSeparator,
        )
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

@InternalApi
public class MapFilesystem(
    private val memFs: MutableMap<String, TestFile> = mutableMapOf(),
    override val filePathSeparator: String = SystemPathSeparator.toString(),
) : Filesystem {
    override suspend fun readFileOrNull(path: String): ByteArray? = memFs[path]?.contents
    override suspend fun writeFile(path: String, data: ByteArray): Unit = write(path, data, WriteType.OVERWRITE)

    override fun fileExists(path: String): Boolean = memFs[path] != null
    override fun write(
        path: String,
        data: ByteArray,
        writeType: WriteType,
        mustExist: Boolean,
        permissions: String?,
    ) {
        if (memFs[path] == null && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }

        val existing = memFs[path] ?: TestFile(byteArrayOf())

        val newData = when (writeType) {
            is WriteType.OFFSET -> {
                val end = writeType.offset.toInt() + data.size
                val result = existing.contents.copyOf(newSize = maxOf(existing.contents.size, end))
                data.copyInto(result, destinationOffset = writeType.offset.toInt())
                result
            }
            is WriteType.APPEND -> existing.contents + data
            is WriteType.OVERWRITE -> data
        }

        val newPermissions = permissions ?: existing.permissions
        memFs[path] = TestFile(newData, newPermissions)
    }

    @InternalApi
    public fun getFilePermissions(path: String): String = memFs.getValue(path).permissions ?: ""
}
