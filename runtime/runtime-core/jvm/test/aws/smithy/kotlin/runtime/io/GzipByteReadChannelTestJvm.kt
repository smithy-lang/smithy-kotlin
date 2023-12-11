/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import java.util.zip.GZIPInputStream

actual fun decompressGzipBytes(bytes: ByteArray): ByteArray =
    GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }.toByteArray()
