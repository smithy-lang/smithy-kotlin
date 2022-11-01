/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toSdk
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path
import okio.sink as okioSink
import okio.source as okioSource

/**
 * Create a [SdkSource] from the given path and range
 */
public fun Path.source(range: LongRange): SdkSource = source(range.first, range.last)

/**
 * Create a [SdkSource] from the given path and range
 */
public fun Path.source(start: Long = 0L, endInclusive: Long = -1): SdkSource {
    val f = toFile()
    require(f.exists()) { "cannot create SdkSource, file does not exist: $this" }
    require(f.isFile) { "cannot create a SdkSource from a directory: $this" }
    require(endInclusive >= -1L) { "endInclusive must be -1 or greater" }

    val calculatedEndInclusive = if (endInclusive == -1L) f.length() - 1L else endInclusive
    return f.source(start, calculatedEndInclusive)
}

/**
 * Create an [SdkSource] from the given file and range
 */
public fun File.source(start: Long = 0L, endInclusive: Long = length() - 1): SdkSource =
    RandomAccessFileSource(this, start, endInclusive).toSdk()

/**
 * Create an [SdkSource] from the given file and range
 */
public fun File.source(range: LongRange): SdkSource = source(range.first, range.last)

/**
 * Create a new [SdkSink] that writes to the file represented by this path
 */
public fun Path.sink(): SdkSink = toFile().sink()

/**
 * Create a new [SdkSink] that writes to this file
 */
public fun File.sink(): SdkSink = okioSink(false).toSdk()

/**
 * Create a new [SdkSource] that reads from this [InputStream]
 */
public fun InputStream.source(): SdkSource = okioSource().toSdk()

/**
 * Create a new [SdkSource] that reads from this [InputStream]
 */
public fun OutputStream.sink(): SdkSink = okioSink().toSdk()
