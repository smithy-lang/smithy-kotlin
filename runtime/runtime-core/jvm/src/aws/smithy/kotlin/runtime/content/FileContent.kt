/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.*
import java.io.File

/**
 * ByteStream backed by a local [file]
 */
public class FileContent(
    public val file: File,
    public val start: Long = 0,
    public val endInclusive: Long = file.length() - 1,
) : ByteStream.SourceStream() {

    override val isOneShot: Boolean = false
    override val contentLength: Long
        get() = endInclusive - start + 1
    override fun readFrom(): SdkSource = file.source(start, endInclusive)
}

// FIXME - move to IO as utility class
private class FileChannel(
    private val delegate: SdkByteChannel = SdkByteChannel(true),
) : SdkByteChannel by delegate {
    var job: Job? = null

    override fun cancel(cause: Throwable?): Boolean {
        job?.cancel(CancellationException("channel was cancelled", cause))
        return delegate.cancel(cause)
    }

    override fun close(cause: Throwable?): Boolean {
        if (cause != null) {
            job?.cancel(CancellationException("channel was closed with cause", cause))
        }
        return delegate.close(cause)
    }

    override fun close() {
        delegate.close()
    }
}
