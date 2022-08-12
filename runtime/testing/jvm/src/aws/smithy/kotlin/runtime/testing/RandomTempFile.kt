/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.testing

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

/**
 * Extension of File that creates a temporary file with a specified name in
 * Java's temporary directory, as declared in the JRE's system properties. The
 * file is immediately filled with a specified amount of random ASCII data.
 *
 * @see RandomInputStream
 *
 * @param sizeInBytes The amount of random ASCII data, in bytes, for the new temp
 * @param filename The name for the new temporary file, within the Java temp
 * directory as declared in the JRE's system properties.
 * @param binaryData Flag controlling whether binary or character data is used.
 */
public class RandomTempFile(
    sizeInBytes: Long,
    filename: String = UUID.randomUUID().toString(),
    private val binaryData: Boolean = false,
) : File(TEMP_DIR + separator + System.currentTimeMillis().toString() + "-" + filename) {

    init {
        createFile(sizeInBytes)
    }

    @Throws(IOException::class)
    public fun createFile(sizeInBytes: Long) {
        deleteOnExit()
        FileOutputStream(this).use { outputStream ->
            BufferedOutputStream(outputStream).use { bufferedOutputStream ->
                RandomInputStream(sizeInBytes, binaryData).use { inputStream ->
                    inputStream.copyTo(bufferedOutputStream)
                }
            }
        }
    }

    override fun delete(): Boolean {
        if (!super.delete()) {
            throw RuntimeException("Could not delete: $absolutePath")
        }
        return true
    }

    public companion object {
        private val TEMP_DIR: String = System.getProperty("java.io.tmpdir")
    }
}
