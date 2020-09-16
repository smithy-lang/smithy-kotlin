/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.content

/**
 * Container for wrapping a ByteArray as a [ByteStream]
 */
class ByteArrayContent(private val bytes: ByteArray) : ByteStream.Buffer() {
    override val contentLength: Long? = bytes.size.toLong()
    override fun bytes(): ByteArray = bytes
}
