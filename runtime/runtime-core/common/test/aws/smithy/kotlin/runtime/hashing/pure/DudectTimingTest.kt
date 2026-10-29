/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * dudect-style timing leak tests (Reparaz, Balasch, Verbauwhede, "Dude, is my code constant time?", 2017). Each test
 * times one operation on two classes of secret input, a fixed value and fresh random values, interleaved in random
 * order. Welch's t-test then compares the two timing distributions, both whole and cropped at several percentiles to
 * drop noisy outliers. |t| above [T_THRESHOLD] in any crop means the classes are distinguishable, i.e. a timing leak.
 *
 * Testing 6 crops per run raises the chance of a false alarm, so a run over the threshold is repeated with fresh
 * inputs and only fails if the repeat is over it too. Operations near the timer's resolution are timed in batches.
 *
 * Slow and machine-sensitive, so excluded from normal test runs. Run with `-Pdudect` (see runtime-core's build file).
 */
class DudectTimingTest {
    private val n = limbsOf("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")
    private val p = limbsOf("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")
    private val one = IntArray(8).apply { this[0] = 1 }
    private val nMinusOne = n.copyOf().apply { this[0] -= 1 }
    private val pMinusOne = p.copyOf().apply { this[0] -= 1 }
    private var sink = 0

    @Test
    fun testBaseMultiplyIsConstantTime() = assertNoLeak("k * G, k = 1 vs random", 100_000, one, { randomBelow(it, n) }) {
        sink += baseMultiplyAffineX(it)[0]
    }

    @Test
    fun testBaseMultiplySparseScalarIsConstantTime() {
        // Only the top nibble set: 63 zero nibbles, at the far end of the table from k = 1
        val sparse = IntArray(8).apply { this[7] = 0x10000000 }
        assertNoLeak("k * G, k = 2^252 vs random", 100_000, sparse, { randomBelow(it, n) }) {
            sink += baseMultiplyAffineX(it)[0]
        }
    }

    @Test
    fun testBaseMultiplyMaxScalarIsConstantTime() = assertNoLeak("k * G, k = n - 1 vs random", 100_000, nMinusOne, { randomBelow(it, n) }) {
        sink += baseMultiplyAffineX(it)[0]
    }

    @Test
    fun testScalarInverseIsConstantTime() = assertNoLeak("safegcd mod n, 1 vs random", 200_000, one, { randomBelow(it, n) }) {
        sink += invertModN(it)[0]
    }

    @Test
    fun testScalarInverseMaxIsConstantTime() = assertNoLeak("safegcd mod n, n - 1 vs random", 200_000, nMinusOne, { randomBelow(it, n) }) {
        sink += invertModN(it)[0]
    }

    @Test
    fun testFieldInverseIsConstantTime() = assertNoLeak("invert mod p, 1 vs random", 100_000, one, { randomBelow(it, p) }) {
        sink += SolinasP256Field.invert(it)[0]
    }

    @Test
    fun testFieldMultiplyIsConstantTime() {
        // 0 * 0 makes every Solinas reduction term zero
        val zero = IntArray(8)
        assertNoLeak("multiply mod p, 0 vs random", 100_000, zero, { randomBelow(it, p) }, batch = FIELD_BATCH) {
            sink += SolinasP256Field.multiply(it, it)[0]
        }
    }

    @Test
    fun testFieldMultiplyMaxCarryIsConstantTime() {
        // (p - 1)^2 makes every product word and every reduction term as large as it gets, so the carries and the
        // folds of `top` in reduce are at their extreme
        assertNoLeak("multiply mod p, p - 1 vs random", 100_000, pMinusOne, { randomBelow(it, p) }, batch = FIELD_BATCH) {
            sink += SolinasP256Field.multiply(it, it)[0]
        }
    }

    @Test
    fun testScalarMultiplyIsConstantTime() {
        // The nonce-dependent mod-n steps of signing (k^-1 times (h + r * d)) are Montgomery multiplies
        assertNoLeak("multiply mod n, n - 1 vs random", 100_000, nMinusOne, { randomBelow(it, n) }, batch = FIELD_BATCH) {
            sink += p256ScalarField.multiply(it, it)[0]
        }
    }

    @Test
    fun testSignIsConstantTime() {
        // Every sample, in both classes, signs a fresh random message, so the nonce varies in both classes and only the
        // key tells them apart
        val fixedKey = ByteArray(32).also { it[31] = 1 }
        assertNoLeak(
            "sign, key = 1 vs random, random messages",
            100_000,
            fixed = { random: Random -> SignInput(fixedKey.copyOf(), random.nextBytes(64)) },
            random = { random -> SignInput(randomKey(random), random.nextBytes(64)) },
        ) {
            sink += pureEcdsaSecp256r1(it.key, it.message)[5]
        }
    }

    /** Positive control: variable-time double-and-add k * G must be caught, or the harness can't see leaks */
    @Test
    fun testReferenceBaseMultiplyLeaks() {
        val result = measure(20_000, Random(SEED), { one.copyOf() }, { randomBelow(it, n) }, 1) {
            sink += referenceBaseMultiplyAffineX(it)[0]
        }
        report("CONTROL double-and-add k * G, k = 1 vs random", result)
        assertTrue(result.maxAbsT > T_THRESHOLD, "control: expected a leak, got max |t| = ${result.maxAbsT}")
    }

    /**
     * Positive control for the batched configuration the field tests use: an early-exit zero check, a few nanoseconds
     * slower on the all-zero fixed input, must still be caught when timed [FIELD_BATCH] at a time
     */
    @Test
    fun testBatchedEarlyExitLeaks() {
        val zero = IntArray(8)
        val result = measure(100_000, Random(SEED), { zero.copyOf() }, { randomBelow(it, p) }, FIELD_BATCH) {
            var i = 7
            while (i >= 0 && it[i] == 0) i--
            sink += i
        }
        report("CONTROL batched early-exit zero check, 0 vs random", result)
        assertTrue(result.maxAbsT > T_THRESHOLD, "batched control: expected a leak, got max |t| = ${result.maxAbsT}")
    }

    private class SignInput(val key: ByteArray, val message: ByteArray)

    private fun assertNoLeak(label: String, samples: Int, fixed: IntArray, random: (Random) -> IntArray, batch: Int = 1, op: (IntArray) -> Unit) = assertNoLeak(label, samples, { fixed.copyOf() }, random, batch, op)

    /**
     * [fixed] is called for every fixed-class sample, so each gets its own input object and the fixed class isn't
     * favored by always hitting one cache line
     */
    private fun <T> assertNoLeak(label: String, samples: Int, fixed: (Random) -> T, random: (Random) -> T, batch: Int = 1, op: (T) -> Unit) {
        val first = measure(samples, Random(SEED), fixed, random, batch, op)
        report(label, first)
        if (first.maxAbsT < T_THRESHOLD) return

        val repeat = measure(samples, Random(SEED + 1), fixed, random, batch, op)
        report("$label (repeat)", repeat)
        assertTrue(repeat.maxAbsT < T_THRESHOLD, "$label: timing leak, max |t| = ${first.maxAbsT} then ${repeat.maxAbsT}")
    }

    private class Result(val tValues: List<Pair<String, Double>>, val medianNanos: Long) {
        val maxAbsT: Double get() = tValues.maxOf { abs(it.second) }
    }

    private fun report(label: String, result: Result) {
        val ts = result.tValues.joinToString(" ") { (name, t) -> "$name=${t.format()}" }
        println("DUDECT | $label | median ${result.medianNanos} ns | max |t| ${result.maxAbsT.format()} | $ts")
    }

    /** Times [batch] back-to-back calls of [op] per sample, each on a different input of that sample's class */
    private fun <T> measure(samples: Int, random: Random, fixedInput: (Random) -> T, randomInput: (Random) -> T, batch: Int, op: (T) -> Unit): Result {
        // Inputs are generated up front so their cost isn't timed
        val classes = BooleanArray(samples) { random.nextBoolean() }
        val inputs = List(samples) { i -> List(batch) { if (classes[i]) fixedInput(random) else randomInput(random) } }

        // Warm up (JIT) on both classes
        repeat(minOf(samples, 5_000)) { i -> inputs[i].forEach(op) }

        val times = LongArray(samples)
        for (i in 0 until samples) {
            val batchInputs = inputs[i]
            val start = TimeSource.Monotonic.markNow()
            for (j in 0 until batch) op(batchInputs[j])
            times[i] = start.elapsedNow().inWholeNanoseconds
        }

        // As in dudect: test the whole set plus sets cropped at percentiles of the pooled timings
        val sorted = times.sorted()
        val cutoffs = listOf("all" to Long.MAX_VALUE) +
            listOf(50, 75, 90, 95, 99).map { pct -> "p$pct" to sorted[sorted.size * pct / 100] }
        val tValues = cutoffs.mapNotNull { (name, cutoff) ->
            val fixedStats = Welford()
            val randomStats = Welford()
            for (i in 0 until samples) {
                if (times[i] > cutoff) continue
                if (classes[i]) fixedStats.add(times[i].toDouble()) else randomStats.add(times[i].toDouble())
            }
            // A crop that leaves too few samples in either class can't estimate a variance
            if (fixedStats.count < MIN_CLASS_SAMPLES || randomStats.count < MIN_CLASS_SAMPLES) null else name to welchT(fixedStats, randomStats)
        }
        check(tValues.isNotEmpty()) { "too few samples per class" }
        return Result(tValues, sorted[sorted.size / 2] / batch)
    }

    /** Running mean and variance */
    private class Welford {
        var count = 0
        var mean = 0.0
        var m2 = 0.0

        fun add(x: Double) {
            count++
            val delta = x - mean
            mean += delta / count
            m2 += delta * (x - mean)
        }

        val variance: Double get() = if (count > 1) m2 / (count - 1) else 0.0
    }

    /** Welch's t. With no variance in either class, any difference in means is a certain leak. */
    private fun welchT(a: Welford, b: Welford): Double {
        val se = sqrt(a.variance / a.count + b.variance / b.count)
        return when {
            se > 0.0 -> (a.mean - b.mean) / se
            a.mean == b.mean -> 0.0
            else -> Double.POSITIVE_INFINITY
        }
    }

    private fun Double.format(): String = (kotlin.math.round(this * 100) / 100).toString()

    private fun randomKey(random: Random): ByteArray = randomBelow(random, n).let { limbs ->
        ByteArray(32).also { out ->
            for (i in 0 until 8) out.putIntBe(28 - 4 * i, limbs[i])
        }
    }

    /** Uniform nonzero value below [modulus] */
    private fun randomBelow(random: Random, modulus: IntArray): IntArray {
        while (true) {
            val candidate = IntArray(8) { random.nextInt() }
            if (candidate.any { it != 0 } && lessThan(candidate, modulus)) return candidate
        }
    }

    private fun lessThan(a: IntArray, b: IntArray): Boolean {
        for (i in 7 downTo 0) {
            if (a[i] != b[i]) return a[i].toUInt() < b[i].toUInt()
        }
        return false
    }

    private companion object {
        /** dudect's threshold for "probably not constant time" */
        const val T_THRESHOLD = 4.5

        const val SEED = 0xdec7

        const val MIN_CLASS_SAMPLES = 100

        /** A field multiply takes about 100 ns, close to the timer's overhead, so time 16 per sample */
        const val FIELD_BATCH = 16
    }
}
