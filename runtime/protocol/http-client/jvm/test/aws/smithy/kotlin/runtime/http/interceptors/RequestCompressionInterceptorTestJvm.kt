/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.interceptors

import java.util.zip.GZIPInputStream

actual fun decompressGzipBytes(compressed: ByteArray): ByteArray =
    GZIPInputStream(compressed.inputStream()).readAllBytes()
