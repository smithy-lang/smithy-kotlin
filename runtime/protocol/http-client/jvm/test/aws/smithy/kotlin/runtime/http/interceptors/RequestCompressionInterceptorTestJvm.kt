/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import java.util.zip.GZIPInputStream

internal actual fun decompressGzipBytes(bytes: ByteArray): ByteArray =
    GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
