/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.http.HttpRequestBodyStream
import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.buffer
import aws.smithy.kotlin.runtime.util.InternalApi

/**
 * Implement's [HttpRequestBodyStream] which proxies an SDK source [SdkSource]
 */
@InternalApi
public class SdkSourceBodyStream(
    source: SdkSource,
) : HttpRequestBodyStream {

    private val source = source.buffer()

    // lie - CRT tries to control this via normal seek operations (e.g. when they calculate a hash for signing
    // they consume the aws_input_stream and then seek to the beginning). Instead we either support creating
    // a new read channel or we don't. At this level we don't care, consumers of this type need to understand
    // and handle these concerns.
    override fun resetPosition(): Boolean = true

    override fun sendRequestBody(buffer: MutableBuffer): Boolean {
        if (source.exhausted()) return true

        val sink = ByteArray(minOf(buffer.writeRemaining, source.buffer.size.toInt()))
        val rc = source.read(sink)
        if (rc == -1) return true

        val wc = buffer.write(sink, 0, rc)
        // sanity check
        check(rc == wc) { "Expected to write $rc bytes but wrote $wc bytes" }

        // if any data is still available from the underlying source then we aren't done
        return source.exhausted()
    }
}
