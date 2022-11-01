/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.io.internal.toSdk

public interface SdkSink : Closeable {

    public companion object {
        /**
         * Returns a sink that writes to nowhere
         */
        public fun blackhole(): SdkSink = okio.blackholeSink().toSdk()
    }

    /**
     * Removes [byteCount] bytes from [source] and appends them to this.
     */
    @Throws(IOException::class)
    public fun write(source: SdkBuffer, byteCount: Long): Unit

    /**
     * Pushes all buffered bytes to their final destination
     */
    @Throws(IOException::class)
    public fun flush(): Unit

    /**
     * Pushes all buffered bytes to their final destination and releases resources held by this sink.
     * It is an error to write to a closed sink. This is an idempotent operation.
     */
    @Throws(IOException::class)
    override fun close(): Unit
}
