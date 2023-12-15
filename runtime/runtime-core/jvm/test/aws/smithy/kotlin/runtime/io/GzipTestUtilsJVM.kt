/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import java.util.zip.GZIPInputStream

/**
 * Decompresses a byte array compressed using the gzip format
 */
internal actual fun decompressGzipBytes(bytes: ByteArray): ByteArray =
    GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
