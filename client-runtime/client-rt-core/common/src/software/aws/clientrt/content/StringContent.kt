/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.content

/**
 * Container for wrapping a String as a [ByteStream]
 */
class StringContent(str: String) : ByteStream.Buffer() {
    @OptIn(ExperimentalStdlibApi::class)
    private val asBytes: ByteArray = str.encodeToByteArray()

    override val contentLength: Long? = asBytes.size.toLong()

    override fun bytes(): ByteArray = asBytes
}
