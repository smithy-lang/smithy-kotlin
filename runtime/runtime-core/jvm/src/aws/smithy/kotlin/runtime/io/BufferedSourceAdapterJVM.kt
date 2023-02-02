/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import java.io.InputStream
import java.nio.ByteBuffer

internal actual class BufferedSourceAdapter actual constructor(
    source: okio.BufferedSource,
) : AbstractBufferedSourceAdapter(source), SdkBufferedSource {

    override fun read(dst: ByteBuffer): Int = delegate.read(dst)

    override fun isOpen(): Boolean = delegate.isOpen

    override fun inputStream(): InputStream = delegate.inputStream()
}
