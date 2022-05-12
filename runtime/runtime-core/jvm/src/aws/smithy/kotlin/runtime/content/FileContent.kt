/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.readChannel
import java.io.File

/**
 * ByteStream backed by a local [file]
 */
public class FileContent(
    public val file: File,
    public val start: Long = 0,
    public val endInclusive: Long = file.length() - 1
) : ByteStream.ReplayableStream() {

    override val contentLength: Long
        get() = if (isPartial()) partialContentLength() else file.length()

    private fun isPartial() = start != 0L || endInclusive != file.length() - 1

    private fun partialContentLength() = endInclusive - start + 1

    override fun newReader(): SdkByteReadChannel = file.readChannel(start, endInclusive)
}
