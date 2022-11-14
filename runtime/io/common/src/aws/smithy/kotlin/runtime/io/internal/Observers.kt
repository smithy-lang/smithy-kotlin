/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Base class for implementing more advanced behavior (e.g. hashing source)
 */
@InternalApi
public abstract class SdkSourceObserver(
    private val delegate: SdkSource,
) : SdkSource by delegate {
    private val cursor = okio.Buffer.UnsafeCursor()

    override fun read(sink: SdkBuffer, limit: Long): Long {
        val okioBuffer = sink.toOkio()

        // transfer bytes from source to sink
        val rc = delegate.read(sink, limit)
        if (rc <= 0L) return rc

        // iterate over internal segments that were just written to sink
        okioBuffer.readUnsafe(cursor)
        try {
            var remaining = rc
            var length = cursor.seek(sink.size - rc)

            while (remaining > 0 && length > 0) {
                val toObserve = minOf(length, remaining.toInt())
                val data = checkNotNull(cursor.data)
                observe(data, cursor.start, toObserve)
                remaining -= toObserve
                length = cursor.next()
            }
        } finally {
            cursor.close()
        }

        return rc
    }

    /**
     * Callback function invoked with access to the raw data being read. Only
     * data in the range [offset, offset+length) is valid! Do not go out of
     * bounds or mutate the data!
     */
    public abstract fun observe(data: ByteArray, offset: Int, length: Int)
}

/**
 * Base class for implementing more advanced behavior (e.g. hashing sink)
 */
@InternalApi
public abstract class SdkSinkObserver(
    private val delegate: SdkSink,
) : SdkSink by delegate {
    private val cursor = okio.Buffer.UnsafeCursor()

    override fun write(source: SdkBuffer, byteCount: Long) {
        val okioBuffer = source.toOkio()

        okioBuffer.readUnsafe(cursor)
        try {
            var length = cursor.seek(0)
            var remaining = byteCount

            while (length > 0 && remaining > 0) {
                val toObserve = minOf(length, remaining.toInt())
                val data = checkNotNull(cursor.data)
                observe(data, cursor.start, toObserve)
                remaining -= toObserve
                length = cursor.next()
            }
        } finally {
            cursor.close()
        }

        delegate.write(source, byteCount)
    }

    /**
     * Callback function invoked with access to the raw data being written. Only
     * data in the range [offset, offset+length) is valid! Do not go out of
     * bounds or mutate the data!
     */
    public abstract fun observe(data: ByteArray, offset: Int, length: Int)
}
