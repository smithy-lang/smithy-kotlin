/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import java.io.OutputStream
import java.nio.ByteBuffer

internal actual class BufferedSinkAdapter actual constructor(
    sink: okio.BufferedSink,
) : AbstractBufferedSinkAdapter(sink), SdkBufferedSink {
    override fun write(src: ByteBuffer): Int = delegate.write(src)

    override fun isOpen(): Boolean = delegate.isOpen

    override fun outputStream(): OutputStream = delegate.outputStream()
}
