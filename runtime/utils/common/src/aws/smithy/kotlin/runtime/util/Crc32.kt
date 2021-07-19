/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.runtime.util

/**
 * CRC-32 checksum. Note: [digest] will return the bytes (big endian) of the CRC32 integer value.
 * Access the [value] directly to avoid doing the integer conversion yourself
 */
expect class Crc32() : HashFunction {
    /**
     * The CRC-32 value
     */
    val value: Long
}
