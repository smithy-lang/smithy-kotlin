/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.io.use
import java.util.zip.GZIPInputStream

actual fun decompressGzipBytes(compressed: ByteArray): ByteArray =
    GZIPInputStream(compressed.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }.toByteArray()
