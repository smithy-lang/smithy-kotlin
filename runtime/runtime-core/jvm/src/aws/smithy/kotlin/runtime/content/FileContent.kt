/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.content

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.readChannel
import java.io.File

/**
 * ByteStream backed by a local [file]
 */
public class FileContent(
    public val file: File,
) : ByteStream.Reader() {

    override val contentLength: Long
        get() = file.length()

    override fun readFrom(): SdkByteReadChannel = file.readChannel()
}
