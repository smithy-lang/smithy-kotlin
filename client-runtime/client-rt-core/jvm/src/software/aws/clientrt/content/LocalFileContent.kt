/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.aws.clientrt.content

import software.aws.clientrt.io.SdkByteReadChannel
import software.aws.clientrt.io.readChannel
import java.io.File

/**
 * ByteStream backed by a local [file]
 */
public class LocalFileContent(
    public val file: File,
) : ByteStream.Reader() {

    override val contentLength: Long
        get() = file.length()

    override fun readFrom(): SdkByteReadChannel = file.readChannel()
}
