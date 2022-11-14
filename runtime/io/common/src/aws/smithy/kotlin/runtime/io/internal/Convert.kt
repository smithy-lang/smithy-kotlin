/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io.internal

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSink
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.util.InternalApi

// Conversion functions to go to and from okio for internal use only. These should not be depended on outside the runtime!

@InternalApi
public fun SdkBuffer.toOkio(): okio.Buffer = inner

@InternalApi
public fun okio.Buffer.toSdk(): SdkBuffer = SdkBuffer(this)

@InternalApi
public fun SdkSource.toOkio(): okio.Source = when (this) {
    is OkioSdkSource -> delegate
    else -> OkioSource(this)
}

@InternalApi
public fun SdkSink.toOkio(): okio.Sink = when (this) {
    is OkioSdkSink -> delegate
    else -> OkioSink(this)
}

@InternalApi
public fun okio.Sink.toSdk(): SdkSink = when (this) {
    is OkioSink -> delegate
    else -> OkioSdkSink(this)
}

@InternalApi
public fun okio.Source.toSdk(): SdkSource = when (this) {
    is OkioSource -> delegate
    else -> OkioSdkSource(this)
}

/**
 * Wrap SDK type [delegate] as an [okio.Sink]
 */
private class OkioSink(
    internal val delegate: SdkSink,
) : okio.Sink {
    override fun close() = delegate.close()

    override fun flush() = delegate.flush()

    // FIXME - is this what we want?
    override fun timeout(): okio.Timeout = okio.Timeout.NONE

    override fun write(source: okio.Buffer, byteCount: Long) {
        delegate.write(SdkBuffer(source), byteCount)
    }
}

/**
 * Wrap SDK type [delegate] as an [okio.Source]
 */
private class OkioSource(
    internal val delegate: SdkSource,
) : okio.Source {
    override fun close() = delegate.close()

    // FIXME is this what we want?
    override fun timeout(): okio.Timeout = okio.Timeout.NONE

    override fun read(sink: okio.Buffer, byteCount: Long): Long =
        delegate.read(SdkBuffer(sink), byteCount)
}

/**
 * Wrap an okio [okio.Source] as an [SdkSource]
 */
private class OkioSdkSource(
    internal val delegate: okio.Source,
) : SdkSource {

    override fun close() = delegate.close()

    override fun read(sink: SdkBuffer, limit: Long): Long =
        delegate.read(sink.toOkio(), limit)
}

/**
 * Wrap an okio [okio.Sink] as an [SdkSink]
 */
private class OkioSdkSink(
    internal val delegate: okio.Sink,
) : SdkSink {
    override fun close() = delegate.close()
    override fun flush() = delegate.flush()

    override fun write(source: SdkBuffer, byteCount: Long) {
        delegate.write(source.toOkio(), byteCount)
    }
}
