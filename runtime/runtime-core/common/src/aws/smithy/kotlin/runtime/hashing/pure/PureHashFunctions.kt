/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.hashing.Crc32Base
import aws.smithy.kotlin.runtime.hashing.HashFunction

// The public surface of this package. The implementation classes stay internal so their structure (block hooks,
// state snapshots) isn't frozen into runtime-core's ABI.

/** Pure-Kotlin CRC32 (IEEE 802.3) */
@InternalApi
public fun pureCrc32(): Crc32Base = PureCrc32()

/** Pure-Kotlin CRC32C (Castagnoli) */
@InternalApi
public fun pureCrc32c(): Crc32Base = PureCrc32c()

/** Pure-Kotlin MD5 */
@InternalApi
public fun pureMd5(): HashFunction = PureMd5()

/** Pure-Kotlin SHA-1 */
@InternalApi
public fun pureSha1(): HashFunction = PureSha1()

/** Pure-Kotlin SHA-256 */
@InternalApi
public fun pureSha256(): HashFunction = PureSha256()

/**
 * Returns an HMAC-MD5 function keyed with [key]. The key-pad states are computed once here, so each call of the
 * returned function skips two block compressions. The returned function is not thread-safe, and it retains
 * key-derived state for its lifetime.
 */
@InternalApi
public fun pureHmacMd5(key: ByteArray): (ByteArray) -> ByteArray = PureHmac(key, PureMd5())::mac

/** Returns an HMAC-SHA1 function keyed with [key]. See [pureHmacMd5]. */
@InternalApi
public fun pureHmacSha1(key: ByteArray): (ByteArray) -> ByteArray = PureHmac(key, PureSha1())::mac

/** Returns an HMAC-SHA256 function keyed with [key]. See [pureHmacMd5]. */
@InternalApi
public fun pureHmacSha256(key: ByteArray): (ByteArray) -> ByteArray = PureHmac(key, PureSha256())::mac

/** Validates a slice of an array of [size] bytes, without the overflow of `offset + length <= size` */
internal fun checkRange(size: Int, offset: Int, length: Int) {
    require(offset >= 0 && length >= 0 && length <= size - offset) {
        "invalid offset/length: offset=$offset, length=$length, size=$size"
    }
}
