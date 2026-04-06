/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.util

import kotlinx.io.IOException
import kotlinx.io.files.FileNotFoundException
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import okio.FileSystem as OkioFileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

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

    /**
     * TODO: KDocs
     */
    public fun write(path: String, data: ByteArray, writeType: WriteType, mustExist: Boolean = false)
    // TODO: Expect implementations

    /**
     * TODO: KDocs
     */
    public fun atomicMove(source: String, destination: String, mustExist: Boolean = true, overwrite: Boolean = false): Unit {
        val sourcePath = Path(source)
        val destinationPath = Path(destination)

        if (!SystemFileSystem.exists(sourcePath) && mustExist) {
            throw FileNotFoundException("$sourcePath does not exist and mustExist is set to true")
        }

        if (SystemFileSystem.exists(destinationPath) && !overwrite) {
            // TODO: Is this right?
            throw IOException("$destinationPath already exists and overwrite is set to false")
        }

        SystemFileSystem.atomicMove(sourcePath, destinationPath)
    }

    /**
     * TODO: KDocs
     */
    public fun delete(path: String, mustExist: Boolean = true): Unit =
        SystemFileSystem.delete(Path(path), mustExist)

    /**
     * TODO: KDocs
     */
    public fun list(path: String, mustExist: Boolean = true): Collection<String> {
        val path = Path(path)
        if (!SystemFileSystem.exists(path) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }
        return SystemFileSystem.list(path).map { it.name }
    }

    /**
     * TODO: KDocs
     */
    public fun createDir(path: String, mustCreate: Boolean = false): Unit =
        SystemFileSystem.createDirectories(Path(path), mustCreate)

    /**
     * TODO: KDocs
     */
    public fun size(path: String, mustExist: Boolean = true): Long {
        val path = Path(path)
        if (!SystemFileSystem.exists(path) && mustExist) {
            throw FileNotFoundException("$path does not exist and mustExist is set to true")
        }

        return SystemFileSystem.metadataOrNull(path)?.size ?: throw IOException("Unable to find size for $path, found null")
    }

    /**
     * TODO: KDocs
     */
    public fun read(path: String, amount: Long, offset: Long = 0, readAll: Boolean = false, mustExist: Boolean = true): ByteArray {
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

public sealed class WriteType {
    public object APPEND : WriteType()
    public object OVERWRITE : WriteType()
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
}
