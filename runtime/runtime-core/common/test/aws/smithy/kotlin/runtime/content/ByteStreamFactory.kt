/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.SdkSource
import aws.smithy.kotlin.runtime.io.source

fun interface ByteStreamFactory {
    fun byteStream(input: ByteArray): ByteStream
    companion object {
        val BYTE_ARRAY: ByteStreamFactory = ByteStreamFactory { input -> ByteStream.fromBytes(input) }

        val SDK_SOURCE: ByteStreamFactory = ByteStreamFactory { input ->
            object : ByteStream.SourceStream() {
                override fun readFrom(): SdkSource = input.source()
                override val contentLength: Long = input.size.toLong()
            }
        }

        val SDK_CHANNEL: ByteStreamFactory = ByteStreamFactory { input ->
            object : ByteStream.ChannelStream() {
                override fun readFrom(): SdkByteReadChannel = SdkByteReadChannel(input)
                override val contentLength: Long = input.size.toLong()
            }
        }
    }
}
