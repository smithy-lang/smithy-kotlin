/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.compression

import aws.smithy.kotlin.runtime.InternalApi

@InternalApi
public actual fun decompressGzipBytes(bytes: ByteArray): ByteArray = TODO("Not yet implemented on wasmJs: gzip")
