/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.benchmarks.crypto.md5

import aws.smithy.kotlin.runtime.util.crypto.Md5
import java.security.MessageDigest
import kotlinx.benchmark.*

private fun <T> Sequence<T>.repeating(): Sequence<T> {
    val items = this
    return sequence { while (true) { yieldAll(items) } }
}

private fun Sequence<Byte>.toByteArray(size: Int): ByteArray {
    val iterator = iterator()
    return ByteArray(size) { iterator.next() }
}

private const val SMALL_PAYLOAD = "small"
private val smallPayloads = ('a'..'z').asSequence().map { it.toString().encodeToByteArray() }

private const val MEDIUM_PAYLOAD = "medium"
private val mediumPayloads = (0..1023).asSequence().map { start ->
    (start until start + 1024)
        .map { c -> c.mod(1024).toByte() }
        .toByteArray()
}

private val largePayload = (0..1023).asSequence().map { it.toByte() }.repeating().toByteArray(10_485_760)
private const val LARGE_PAYLOAD = "large"

private val payloads = mapOf(
    SMALL_PAYLOAD to smallPayloads.repeating().take(10_000).toList(), // 10,000x 1B payloads
    MEDIUM_PAYLOAD to mediumPayloads.toList(), // 1,024x 1KB payloads
    LARGE_PAYLOAD to listOf(largePayload), // 1x 10MB payload
)

@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class Md5Benchmark {
    @Param(SMALL_PAYLOAD, MEDIUM_PAYLOAD, LARGE_PAYLOAD)
    var payloadName: String = ""

    @Benchmark
    fun jvmMd5Benchmark() {
        payloads.getValue(payloadName).forEach {
            val md5 = MessageDigest.getInstance("MD5")
            md5.update(it)
            md5.digest()
        }
    }

    @Benchmark
    fun kmpMd5Benchmark() {
        payloads.getValue(payloadName).forEach {
            val md5 = Md5()
            md5.append(it)
            md5.compute()
        }
    }
}
