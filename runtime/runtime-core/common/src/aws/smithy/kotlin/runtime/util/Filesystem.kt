/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.PlannedRemoval
import aws.smithy.kotlin.runtime.io.IOException
import aws.smithy.kotlin.runtime.io.internal.SdkDispatchers
import kotlinx.coroutines.withContext
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
 *
 * On JVM, IO failures are reported as `java.io.IOException`. On Native, the smithy-kotlin, kotlinx-io and okio
 * exception hierarchies are distinct classes, so an IO failure may surface as any of them; catch [Exception] there.
 * Invalid arguments and unsupported operations (e.g. a cross-volume [rename]) may throw other exception types.
 */
@InternalApi
public interface Filesystem {

    /**
     * The delimiter of segments in a path. For example in Linux: /home/user/documents
     * or Windows: C:\Program Files\Notepad.EXE
     */
    public val filePathSeparator: String

    // Suspending API. Implementations must not block the calling thread; the defaults below move the work onto
    // SdkDispatchers.IO and delegate to the deprecated blocking members so existing implementations keep working.

    /**
     * Read the entire contents of a file, or return null if the file does not exist or cannot be read.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem.
     * @return contents of file or null if error (file does not exist, etc.)
     */
    public suspend fun readFileOrNull(path: String): ByteArray?

    /**
     * Read the entire contents of a file.
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @throws FileNotFoundException if the file does not exist
     * @throws IOException if the file cannot be read
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun readFile(path: String): ByteArray = withContext(SdkDispatchers.IO) { read(path, readAll = true) }

    /**
     * Read [length] bytes from a file, starting at byte [offset].
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @param offset byte offset to start reading from
     * @param length number of bytes to read
     * @throws FileNotFoundException if the file does not exist
     * @throws IOException if the file cannot be read or holds fewer than [offset] + [length] bytes
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun readFile(path: String, offset: Long, length: Long): ByteArray = withContext(SdkDispatchers.IO) { read(path, amount = length, offset = offset) }

    /**
     * Write the contents of a file. File will be created if it doesn't exist. Existing files will be overwritten.
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     * @param data the file contents to write to disk
     */
    public suspend fun writeFile(path: String, data: ByteArray): Unit = writeFile(path, data, WriteType.OVERWRITE)

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
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun writeFile(
        path: String,
        data: ByteArray,
        writeType: WriteType,
        mustExist: Boolean = false,
        permissions: String? = null,
    ): Unit = withContext(SdkDispatchers.IO) { write(path, data, writeType, mustExist, permissions) }

    /**
     * Check if a file or directory exists at [path].
     *
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun pathExists(path: String): Boolean = withContext(SdkDispatchers.IO) { exists(path) }

    /**
     * Get the size of the file at [path] in bytes.
     *
     * @param path fully qualified path of the file
     * @throws FileNotFoundException if [path] does not exist
     * @throws IOException if the size cannot be determined
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun fileSize(path: String): Long = withContext(SdkDispatchers.IO) { size(path) }

    /**
     * List the names of files and directories within the directory at [path].
     *
     * @param path fully qualified path of the directory to list
     * @throws FileNotFoundException if [path] does not exist
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun listDirectory(path: String): List<String> = withContext(SdkDispatchers.IO) { list(path).toList() }

    /**
     * Create a directory (and any missing parent directories) at [path].
     *
     * @param path fully qualified path of the directory to create
     * @param mustCreate if true, throws [IOException] when the directory already exists
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun createDirectories(path: String, mustCreate: Boolean = false): Unit = withContext(SdkDispatchers.IO) { createDir(path, mustCreate) }

    /**
     * Delete the file or empty directory at [path].
     *
     * @param path fully qualified path of the file or directory to delete
     * @param mustExist if true, throws [FileNotFoundException] when nothing exists at [path]
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun remove(path: String, mustExist: Boolean = true): Unit = withContext(SdkDispatchers.IO) { delete(path, mustExist) }

    /**
     * Move the file or directory at [source] to [destination]. The move itself is atomic where the platform supports
     * it, but the [overwrite] check is not: a [destination] created concurrently, after the check, may be replaced.
     *
     * @param source fully qualified path of the file to move
     * @param destination fully qualified path of the target location
     * @param overwrite if false, throws [IOException] when [destination] already exists at the time of the check
     * @throws FileNotFoundException if [source] does not exist
     */
    @OptIn(PlannedRemoval::class)
    @Suppress("DEPRECATION")
    public suspend fun rename(source: String, destination: String, overwrite: Boolean = false): Unit = withContext(SdkDispatchers.IO) { atomicMove(source, destination, mustExist = true, overwrite = overwrite) }

    // Blocking API. Deprecated because these block whichever thread calls them.

    /**
     * Check if a file exists at the [path].
     * @param path fully qualified path encoded specifically to the target platform's filesystem
     */
    @Deprecated("Use pathExists() instead", replaceWith = ReplaceWith("pathExists(path)"))
    @PlannedRemoval(major = 1, minor = 8)
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
    @Deprecated(
        "Blocks the calling thread. Use writeFile() instead",
        replaceWith = ReplaceWith("writeFile(path, data, writeType, mustExist, permissions)"),
    )
    @PlannedRemoval(major = 1, minor = 8)
    public fun write(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean = false, permissions: String? = null)

    /**
     * Atomically move a file from [source] to [destination].
     *
     * @param source fully qualified path of the file to move
     * @param destination fully qualified path of the target location
     * @param mustExist if true, throws [FileNotFoundException] when [source] does not exist
     * @param overwrite if false, throws [IOException] when [destination] already exists
     */
    @Deprecated(
        "Blocks the calling thread. Use rename() instead",
        replaceWith = ReplaceWith("rename(source, destination, overwrite)"),
    )
    @PlannedRemoval(major = 1, minor = 8)
    @Suppress("DEPRECATION") // delegates to other deprecated blocking members
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
    @Deprecated("Blocks the calling thread. Use remove() instead", replaceWith = ReplaceWith("remove(path, mustExist)"))
    @PlannedRemoval(major = 1, minor = 8)
    public fun delete(path: String, mustExist: Boolean = true): Unit = SystemFileSystem.delete(Path(path), mustExist)

    /**
     * List the names of files and directories within the directory at [path].
     *
     * @param path fully qualified path of the directory to list
     * @param mustExist if true, throws [FileNotFoundException] when [path] does not exist
     * @return collection of entry names in the directory
     */
    @Deprecated("Blocks the calling thread. Use listDirectory() instead", replaceWith = ReplaceWith("listDirectory(path)"))
    @PlannedRemoval(major = 1, minor = 8)
    @Suppress("DEPRECATION") // delegates to other deprecated blocking members
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
    @Deprecated(
        "Blocks the calling thread. Use createDirectories() instead",
        replaceWith = ReplaceWith("createDirectories(path, mustCreate)"),
    )
    @PlannedRemoval(major = 1, minor = 8)
    public fun createDir(path: String, mustCreate: Boolean = false): Unit = SystemFileSystem.createDirectories(Path(path), mustCreate)

    /**
     * Get the size of a file at [path] in bytes.
     *
     * @param path fully qualified path of the file
     * @param mustExist if true, throws [FileNotFoundException] when [path] does not exist
     * @return the file size in bytes
     * @throws [IOException] if the size cannot be determined
     */
    @Deprecated("Blocks the calling thread. Use fileSize() instead", replaceWith = ReplaceWith("fileSize(path)"))
    @PlannedRemoval(major = 1, minor = 8)
    @Suppress("DEPRECATION") // delegates to other deprecated blocking members
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
    @Deprecated("Blocks the calling thread. Use readFile() instead")
    @PlannedRemoval(major = 1, minor = 8)
    @Suppress("DEPRECATION") // delegates to other deprecated blocking members
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
    @Deprecated(
        "Blocks the calling thread. Use readFile() instead, or readFileOrNull(), which returns null on any IO error " +
            "rather than only when the file is missing",
    )
    @PlannedRemoval(major = 1, minor = 8)
    @Suppress("DEPRECATION") // delegates to other deprecated blocking members
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
    @Deprecated("Blocks the calling thread. Use pathExists() instead", replaceWith = ReplaceWith("pathExists(path)"))
    @PlannedRemoval(major = 1, minor = 8)
    public fun exists(path: String): Boolean = SystemFileSystem.exists(Path(path))

    public companion object {
        /**
         * Construct a fake filesystem from a mapping of paths to contents
         */
        @Deprecated("Use fromMap(Map<String, TestFile>) instead", replaceWith = ReplaceWith("fromMap(data.mapValues { (_, v) -> TestFile(v) }, filePathSeparator)"))
        @PlannedRemoval(major = 1, minor = 8)
        public fun fromMap(data: Map<String, ByteArray>, filePathSeparator: String = "/"): Filesystem = fromMap(
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

/**
 * An in-memory [Filesystem] meant for testing. Directories are implied by the files beneath them, plus any created
 * via [createDirectories]. The deprecated blocking members other than [write] and [fileExists] are not overridden and
 * still operate on the real filesystem.
 */
@InternalApi
public class MapFilesystem(
    private val memFs: MutableMap<String, TestFile> = mutableMapOf(),
    override val filePathSeparator: String = SystemPathSeparator.toString(),
) : Filesystem {
    private val dirs = mutableSetOf<String>()

    private fun childPrefix(path: String) = path.removeSuffix(filePathSeparator) + filePathSeparator

    // Strip a trailing separator so "/a/" and "/a" name the same directory
    private fun dirKey(path: String) = if (path.length > filePathSeparator.length) path.removeSuffix(filePathSeparator) else path

    private fun isDirectory(path: String): Boolean {
        val prefix = childPrefix(path)
        return dirKey(path) in dirs || (memFs.keys.asSequence() + dirs).any { it.startsWith(prefix) }
    }

    override suspend fun readFileOrNull(path: String): ByteArray? = memFs[path]?.contents

    override suspend fun readFile(path: String): ByteArray = memFs[path]?.contents ?: throw FileNotFoundException("$path does not exist")

    override suspend fun readFile(path: String, offset: Long, length: Long): ByteArray {
        val contents = readFile(path)
        // Compare against the remaining size rather than offset + length, which can overflow
        if (offset < 0 || length < 0 || offset > contents.size || length > contents.size - offset) {
            throw IOException("Cannot read $length bytes at offset $offset from $path of size ${contents.size}")
        }
        return contents.copyOfRange(offset.toInt(), (offset + length).toInt())
    }

    override suspend fun writeFile(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean, permissions: String?): Unit = writeInMemory(path, data, writeType, mustExist, permissions)

    override suspend fun pathExists(path: String): Boolean = path in memFs || isDirectory(path)

    override suspend fun fileSize(path: String): Long = memFs[path]?.contents?.size?.toLong() ?: throw FileNotFoundException("$path does not exist")

    // Paths beneath [path], excluding [path] itself (the root "/" is its own child prefix)
    private fun descendants(path: String): Sequence<String> {
        val prefix = childPrefix(path)
        val self = dirKey(path)
        return (memFs.keys.asSequence() + dirs).filter { it != self && it.startsWith(prefix) }
    }

    override suspend fun listDirectory(path: String): List<String> {
        if (!isDirectory(path)) throw FileNotFoundException("$path is not a directory")
        val prefix = childPrefix(path)
        return descendants(path)
            .map { it.removePrefix(prefix).substringBefore(filePathSeparator) }
            .distinct()
            .toList()
    }

    override suspend fun createDirectories(path: String, mustCreate: Boolean) {
        if (path in memFs) throw IOException("$path already exists and is a file")
        if (mustCreate && isDirectory(path)) throw IOException("$path already exists and mustCreate is set to true")
        dirs += dirKey(path)
    }

    override suspend fun remove(path: String, mustExist: Boolean) {
        when {
            memFs.remove(path) != null -> Unit
            isDirectory(path) -> {
                if (descendants(path).any()) throw IOException("$path is not empty")
                dirs -= dirKey(path)
            }
            mustExist -> throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }
    }

    override suspend fun rename(source: String, destination: String, overwrite: Boolean) {
        if (isDirectory(source)) throw IOException("MapFilesystem does not support moving directories")
        val file = memFs[source] ?: throw FileNotFoundException("$source does not exist")
        if (isDirectory(destination)) throw IOException("$destination is a directory")
        if (!overwrite && pathExists(destination)) {
            throw IOException("$destination already exists and overwrite is set to false")
        }
        memFs.remove(source)
        memFs[destination] = file
    }

    @OptIn(PlannedRemoval::class)
    @Deprecated("Use pathExists() instead", replaceWith = ReplaceWith("pathExists(path)"))
    override fun fileExists(path: String): Boolean = memFs[path] != null

    @OptIn(PlannedRemoval::class)
    @Deprecated(
        "Blocks the calling thread. Use writeFile() instead",
        replaceWith = ReplaceWith("writeFile(path, data, writeType, mustExist, permissions)"),
    )
    override fun write(
        path: String,
        data: ByteArray,
        writeType: WriteType,
        mustExist: Boolean,
        permissions: String?,
    ): Unit = writeInMemory(path, data, writeType, mustExist, permissions)

    private fun writeInMemory(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean, permissions: String?) {
        if (isDirectory(path)) throw IOException("$path is a directory")
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
