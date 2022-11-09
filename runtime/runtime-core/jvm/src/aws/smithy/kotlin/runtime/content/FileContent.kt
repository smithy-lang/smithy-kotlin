/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import java.io.File

/**
 * ByteStream backed by a local [file]
 */
public class FileContent(
    public val file: File,
    public val start: Long = 0,
    public val endInclusive: Long = file.length() - 1,
) : ByteStream.ReplayableStream() {

    override val contentLength: Long
        get() = endInclusive - start + 1

    // FIXME - is this what we want? Or can we directly consume e.g. `SdkSource` as a bytestream variant?
    override fun newReader(): SdkByteReadChannel = TODO("needs reimplemented")
}
