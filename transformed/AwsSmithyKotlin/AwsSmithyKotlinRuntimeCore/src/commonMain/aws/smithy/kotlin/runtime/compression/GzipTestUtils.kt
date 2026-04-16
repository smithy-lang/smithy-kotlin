/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.InternalApi

/**
 * Decompresses a [ByteArray] compressed using the gzip format
 */
@InternalApi
public expect fun decompressGzipBytes(bytes: ByteArray): ByteArray
