/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.use
import java.util.zip.GZIPInputStream

/**
 * Decompresses a [ByteArray] compressed using the gzip format
 */
@InternalApi
public actual fun decompressGzipBytes(bytes: ByteArray): ByteArray = GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
