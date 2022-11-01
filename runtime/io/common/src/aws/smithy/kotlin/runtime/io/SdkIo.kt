/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toOkio
import okio.buffer

// TODO - Pipe _like_ abstraction but supporting suspend (aka SdkByteChannel) e.g. fun SdkByteChannel(): SdkByteChannel -> AsyncPipe()
// TODO - Hashing Sink/Source + Checksum (CRC)
// TODO - timeouts?
// TODO - request/require/exhausted/emit

/**
 * Returns a new sink that buffers writes to the sink. Writes will be efficiently "batched".
 * Call [SdkSink.flush] when done to emit all data to the underlying sink.
 */
public fun SdkSink.buffer(): SdkBufferedSink = BufferedSinkAdapter(toOkio().buffer())

/**
 * Returns a new source that buffers reads from the underlying source. The returned source
 * will perform bulk reads to an in-memory buffer making small reads efficient.
 */
public fun SdkSource.buffer(): SdkBufferedSource = BufferedSourceAdapter(toOkio().buffer())
