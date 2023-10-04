/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.content

/**
 * Container for wrapping a String as a [ByteStream]
 */
internal class StringContent(str: String) : ByteStream.Buffer() {
    private val asBytes: ByteArray = str.encodeToByteArray()

    override val contentLength: Long = asBytes.size.toLong()

    override fun bytes(): ByteArray = asBytes
}
