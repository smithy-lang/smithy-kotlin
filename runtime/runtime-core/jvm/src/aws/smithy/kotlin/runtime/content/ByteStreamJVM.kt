/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.copyTo
import aws.smithy.kotlin.runtime.io.writeChannel
import java.io.File
import java.nio.file.Path

// JVM specific extensions for dealing with ByteStream's

/**
 * Create a [ByteStream] from a file
 */
fun ByteStream.Companion.fromFile(file: File): ByteStream = file.asByteStream()

/**
 * Create a [ByteStream] from a file
 */
fun File.asByteStream(): ByteStream = FileContent(this)

/**
 * Create a [ByteStream] from a path
 */
fun Path.asByteStream(): ByteStream {
    val f = toFile()
    require(f.exists()) { "cannot create ByteStream, file does not exist: $this" }
    require(f.isFile) { "cannot create a ByteStream from a directory: $this" }
    return f.asByteStream()
}

/**
 * Write the contents of this ByteStream to file and close it
 * @return the number of bytes written
 */
suspend fun ByteStream.writeToFile(file: File): Long {
    val writer = file.writeChannel()
    val src = when (this) {
        is ByteStream.Buffer -> SdkByteReadChannel(bytes())
        is ByteStream.Reader -> readFrom()
    }

    try {
        return src.copyTo(writer)
    } finally {
        writer.close()
        src.close()
    }
}

/**
 * Write the contents of this ByteStream to file at the given path
 * @return the number of bytes written
 */
suspend fun ByteStream.writeToFile(path: Path): Long = writeToFile(path.toFile())
