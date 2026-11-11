/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import aws.smithy.kotlin.runtime.InternalApi
import kotlinx.atomicfu.atomic

/**
 * Pure-Kotlin ECDSA on secp256r1 (P-256) with SHA-256, returning an ASN.1 DER signature. Nonces are derived
 * deterministically per RFC 6979. `s` is returned as computed, not normalized to the low half (s <= n / 2), the same as
 * runtime-core's `ecdsaSecp256r1`.
 *
 * k * G uses a lazily built fixed-base comb table of affine points; field arithmetic mod p uses the NIST/Solinas fast
 * reduction on plain values, arithmetic mod n uses Montgomery multiplication, the mod-p inversion uses a fixed addition
 * chain and the mod-n inversion uses Bernstein-Yang safegcd.
 *
 * The arithmetic on secret values (the private key and the nonce) is written to run in constant time: no branches or
 * table indexes depend on them. Reductions use masks, the comb reads every entry of a table row and adds on every
 * nibble using complete addition formulas, and both inversions run a fixed sequence of steps. Known exceptions, all
 * leaking only rare or public events:
 * - The length of [key]'s encoding is treated as public.
 * - RFC 6979 rejects a nonce candidate of 0 or >= n (probability below 2^-32) and draws another.
 * - A signature with r = 0 or s = 0 (probability about 2^-256) is retried with the next nonce.
 *
 * Kotlin compilers don't promise to preserve constant-time code: LLVM turned masks back into branches until they were
 * hidden behind [opaque]. `DudectTimingTest` checks for timing leaks on the JVM and on optimized native builds; it has
 * been run on linuxX64, and the `Timing leak tests` workflow runs it on the JVM, linuxX64 and macosArm64. Other native
 * targets (other Apple targets, linuxArm64, mingwX64) are unchecked.
 *
 * Named buffers that hold the key, the nonce or their derivatives (the RFC 6979 state, the comb accumulator and
 * selected table entry, the safegcd state, Montgomery products) are zeroed after use. This is best effort: the
 * temporaries allocated by each field operation are not zeroed, and the runtime may still hold copies.
 */
@InternalApi
public fun pureEcdsaSecp256r1(key: ByteArray, message: ByteArray): ByteArray {
    val dBytes = key.toFixed32()
    val d = dBytes.toLimbs()
    try {
        require(!d.isZero() && !geq(d, N)) { "private key out of range" }
        val h = messageScalar(message)
        val nonces = Rfc6979Nonces(dBytes, h.toBytes())
        try {
            return signWithNonces(d, h, nonces::next)
        } finally {
            nonces.wipe()
        }
    } finally {
        dBytes.fill(0)
        d.fill(0)
    }
}

/** SHA-256 of [message] as a scalar mod n */
internal fun messageScalar(message: ByteArray): IntArray = reduceOnce(PureSha256().apply { update(message) }.digest().toLimbs(), N)

/** Signs [h] with private key [d], drawing nonces from [nextNonce] until r and s are both nonzero */
internal fun signWithNonces(d: IntArray, h: IntArray, nextNonce: () -> IntArray): ByteArray {
    while (true) {
        val k = nextNonce()
        val signature = signWithNonce(d, h, k)
        k.fill(0)
        if (signature != null) {
            val body = derInteger(signature.first) + derInteger(signature.second)
            return byteArrayOf(0x30, body.size.toByte()) + body
        }
    }
}

/** (r, s) for private key [d], message scalar [h] and nonce [k], or null if r or s is 0 */
internal fun signWithNonce(d: IntArray, h: IntArray, k: IntArray): Pair<IntArray, IntArray>? {
    val r = reduceOnce(baseMultiplyAffineX(k), N)
    if (r.isZero()) return null

    // s = k^-1 * (h + r * d) mod n. Values stay plain: multiplying a Montgomery-form value by a plain one gives a
    // plain product, so only two conversions are needed.
    val kInv = invertModN(k)
    val kInvMont = Fn.toMontgomery(kInv)
    val rd = Fn.multiply(Fn.toMontgomery(r), d)
    val sum = Fn.add(h, rd)
    val s = Fn.multiply(kInvMont, sum)
    for (secret in arrayOf(kInv, kInvMont, rd, sum)) secret.fill(0)
    return if (s.isZero()) null else r to s
}

// 256-bit unsigned integers are IntArray(8) of little-endian 32-bit limbs
private const val LIMBS = 8
private const val MASK = 0xffffffffL

private fun Int.u(): Long = toLong() and MASK

/**
 * Always 0, but read atomically so compilers can't prove it. XOR-ing a secret mask with it hides that the mask is 0 or
 * -1, which stops LLVM from turning masked arithmetic back into branches (it did so in [normalize30]). libsecp256k1
 * uses `volatile` the same way; a Kotlin `@Volatile` var was constant-folded away on Kotlin/Native.
 *
 * This relies on atomicfu's native IR transformation staying off (`kotlinx.atomicfu.enableNativeIrTransformation=false`
 * in gradle.properties), which keeps `atomic(0)` a real atomic object on Kotlin/Native. With the transformation on, it
 * would become a `@Volatile` field, the form that was folded away. Rerun the native dudect tests if that changes.
 */
private val opaqueZero = atomic(0)

private fun Int.opaque(): Int = this xor opaqueZero.value

/** [opaque] with a value of [opaqueZero] already read by the caller, for hot loops */
private fun Int.opaque(zero: Int): Int = this xor zero

// Comparisons scan every limb instead of exiting early. Callers only branch on their result when it is public (input
// validation, nonce rejection); secret-dependent reductions use the masked helpers below instead.
private fun IntArray.isZero(): Boolean {
    var acc = 0
    for (limb in this) acc = acc or limb
    return acc == 0
}

private fun geq(a: IntArray, b: IntArray): Boolean {
    var borrow = 0L
    for (i in 0 until LIMBS) borrow = (a[i].u() - b[i].u() - borrow) ushr 63
    return borrow == 0L
}

/** out = a + (b and mask), returning the carry. [mask] is 0 or -1. */
private fun addMasked(out: IntArray, a: IntArray, b: IntArray, mask: Int): Int {
    var carry = 0L
    for (i in 0 until LIMBS) {
        val sum = a[i].u() + (b[i] and mask).u() + carry
        out[i] = sum.toInt()
        carry = sum ushr 32
    }
    return carry.toInt()
}

/** out = a - (b and mask), returning the borrow. [mask] is 0 or -1. */
private fun subtractMasked(out: IntArray, a: IntArray, b: IntArray, mask: Int): Int {
    var borrow = 0L
    for (i in 0 until LIMBS) {
        val diff = a[i].u() - (b[i] and mask).u() - borrow
        out[i] = diff.toInt()
        borrow = diff ushr 63
    }
    return borrow.toInt()
}

/**
 * Reduces carry * 2^256 + out (a value below 2m) modulo m in place: subtract m, then add it back if that went negative,
 * i.e. if there was no carry in and a borrow out.
 */
private fun reduceWithCarry(out: IntArray, carry: Int, m: IntArray) {
    val borrow = subtractRaw(out, out, m)
    addMasked(out, out, m, (-(borrow and (carry xor 1))).opaque())
}

/** -1 if [a] == [b], else 0. [zero] is a value read from [opaqueZero]. */
private fun eqMask(a: Int, b: Int, zero: Int): Int {
    val d = a xor b
    return (((d - 1) and d.inv()) shr 31).opaque(zero)
}

/** dst = mask ? src : dst. [mask] is 0 or -1. */
private fun conditionalAssign(dst: IntArray, src: IntArray, mask: Int) {
    for (i in 0 until LIMBS) dst[i] = dst[i] xor ((dst[i] xor src[i]) and mask)
}

/** out = a - b mod 2^256, returning the borrow */
private fun subtractRaw(out: IntArray, a: IntArray, b: IntArray): Int {
    var borrow = 0L
    for (i in 0 until LIMBS) {
        val diff = a[i].u() - b[i].u() - borrow
        out[i] = diff.toInt()
        borrow = diff ushr 63
    }
    return borrow.toInt()
}

/** out = a + b mod 2^256, returning the carry */
private fun addRaw(out: IntArray, a: IntArray, b: IntArray): Int {
    var carry = 0L
    for (i in 0 until LIMBS) {
        val sum = a[i].u() + b[i].u() + carry
        out[i] = sum.toInt()
        carry = sum ushr 32
    }
    return carry.toInt()
}

/** Reduces a value < 2m modulo m (holds for any 256-bit value since both P-256 moduli exceed 2^255) */
internal fun reduceOnce(a: IntArray, m: IntArray): IntArray = a.copyOf().also { reduceWithCarry(it, 0, m) }

/**
 * Left-pads to 32 bytes, or strips excess leading bytes, which must all be zero. The scan covers every byte, but the
 * branch on the encoding's length means that length is treated as public.
 */
private fun ByteArray.toFixed32(): ByteArray {
    val extra = size - 32
    if (extra <= 0) return ByteArray(-extra) + this
    var excess = 0
    for (i in 0 until extra) excess = excess or this[i].toInt()
    require(excess == 0) { "value exceeds 256 bits" }
    return copyOfRange(extra, size)
}

/** Big-endian 32 bytes -> limbs */
private fun ByteArray.toLimbs(): IntArray {
    require(size == 32)
    return IntArray(LIMBS) { getIntBe(28 - 4 * it) }
}

/** Limbs -> big-endian 32 bytes */
private fun IntArray.toBytes(): ByteArray = ByteArray(32).also { out ->
    for (i in 0 until LIMBS) out.putIntBe(28 - 4 * i, this[i])
}

@OptIn(ExperimentalStdlibApi::class)
private fun hexLimbs(hex: String): IntArray = hex.hexToByteArray().toLimbs()

private fun derInteger(value: IntArray): ByteArray {
    var bytes = value.toBytes().dropWhile { it == 0.toByte() }.toByteArray()
    if (bytes.isEmpty() || bytes[0] < 0) bytes = byteArrayOf(0) + bytes
    return byteArrayOf(0x02, bytes.size.toByte()) + bytes
}

private val P = hexLimbs("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")
private val N = hexLimbs("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")

/**
 * Arithmetic modulo an odd 256-bit [modulus] > 2^255 using Montgomery multiplication (CIOS) with R = 2^256. Used for
 * the scalar field mod n.
 */
internal class MontgomeryField(private val modulus: IntArray) {
    private val mPrime: Long // -modulus^-1 mod 2^32
    private val rSquared: IntArray

    init {
        require(modulus[LIMBS - 1] < 0 && modulus[0] and 1 == 1) { "modulus must be odd and exceed 2^255" }

        var inv = 1
        repeat(5) { inv *= 2 - modulus[0] * inv } // Newton iteration doubles correct bits each step
        mPrime = (-inv).u()

        var x = IntArray(LIMBS).also { subtractRaw(it, IntArray(LIMBS), modulus) } // R mod m = 2^256 - m
        repeat(256) { x = add(x, x) }
        rSquared = x
    }

    fun add(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(LIMBS)
        reduceWithCarry(out, addRaw(out, a, b), modulus)
        return out
    }

    fun multiply(a: IntArray, b: IntArray): IntArray {
        val t = LongArray(LIMBS + 2)
        for (i in 0 until LIMBS) {
            val bi = b[i].u()
            var carry = 0L
            for (j in 0 until LIMBS) {
                // Fits in 64 unsigned bits: (2^32-1) + (2^32-1)^2 + (2^32-1) = 2^64 - 1
                val s = t[j] + a[j].u() * bi + carry
                t[j] = s and MASK
                carry = s ushr 32
            }
            var s = t[LIMBS] + carry
            t[LIMBS] = s and MASK
            t[LIMBS + 1] = s ushr 32

            val q = (t[0] * mPrime) and MASK
            s = t[0] + q * modulus[0].u()
            carry = s ushr 32
            for (j in 1 until LIMBS) {
                s = t[j] + q * modulus[j].u() + carry
                t[j - 1] = s and MASK
                carry = s ushr 32
            }
            s = t[LIMBS] + carry
            t[LIMBS - 1] = s and MASK
            t[LIMBS] = t[LIMBS + 1] + (s ushr 32)
        }

        val out = IntArray(LIMBS) { t[it].toInt() }
        reduceWithCarry(out, t[LIMBS].toInt(), modulus)
        t.fill(0) // partial products of the (secret) operands
        return out
    }

    fun toMontgomery(a: IntArray): IntArray = multiply(a, rSquared)
}

/**
 * Arithmetic modulo the P-256 prime p on plain (non-Montgomery) values, reducing products with the NIST/Solinas fast
 * reduction (FIPS 186-4 D.2.3; Guide to Elliptic Curve Cryptography, Algorithm 2.29).
 */
internal object SolinasP256Field {
    val one: IntArray = IntArray(LIMBS).apply { this[0] = 1 }

    fun add(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(LIMBS)
        reduceWithCarry(out, addRaw(out, a, b), P)
        return out
    }

    fun subtract(a: IntArray, b: IntArray): IntArray {
        val out = IntArray(LIMBS)
        addMasked(out, out, P, (-subtractRaw(out, a, b)).opaque())
        return out
    }

    fun multiply(a: IntArray, b: IntArray): IntArray {
        // Schoolbook 256x256 -> 512-bit product as 16 32-bit words
        val c = LongArray(2 * LIMBS)
        for (i in 0 until LIMBS) {
            val ai = a[i].u()
            var carry = 0L
            for (j in 0 until LIMBS) {
                val s = c[i + j] + ai * b[j].u() + carry
                c[i + j] = s and MASK
                carry = s ushr 32
            }
            c[i + LIMBS] = carry
        }
        return reduce(c)
    }

    fun square(a: IntArray): IntArray = multiply(a, a)

    /** a^(2^count) */
    fun squareTimes(a: IntArray, count: Int): IntArray {
        var result = a
        repeat(count) { result = square(result) }
        return result
    }

    /**
     * Inverse a^(p-2) via a fixed addition chain (255 squarings + 12 multiplications); maps 0 to 0.
     * p - 2 = ffffffff 00000001 00000000 00000000 00000000 ffffffff ffffffff fffffffd
     */
    fun invert(a: IntArray): IntArray {
        val x1 = a
        val x2 = multiply(square(x1), x1) // a^(2^2 - 1)
        val x3 = multiply(square(x2), x1)
        val x6 = multiply(squareTimes(x3, 3), x3)
        val x12 = multiply(squareTimes(x6, 6), x6)
        val x15 = multiply(squareTimes(x12, 3), x3)
        val x30 = multiply(squareTimes(x15, 15), x15)
        val x32 = multiply(squareTimes(x30, 2), x2)

        var t = x32 // ffffffff
        t = multiply(squareTimes(t, 32), x1) // 00000001
        t = multiply(squareTimes(t, 128), x32) // 96 zero bits, ffffffff
        t = multiply(squareTimes(t, 32), x32) // ffffffff
        t = multiply(squareTimes(t, 30), x30) // fffffffd = 30 ones, 0, 1
        return multiply(squareTimes(t, 2), x1)
    }

    /**
     * Reduces a 512-bit value c (16 words c0..c15) mod p as s1 + 2s2 + 2s3 + s4 + s5 - s6 - s7 - s8 - s9, with the
     * terms collected per output word and carries propagated as signed values.
     */
    private fun reduce(c: LongArray): IntArray {
        val out = IntArray(LIMBS)
        var acc = c[0] + c[8] + c[9] - c[11] - c[12] - c[13] - c[14]
        out[0] = acc.toInt()
        acc = (acc shr 32) + c[1] + c[9] + c[10] - c[12] - c[13] - c[14] - c[15]
        out[1] = acc.toInt()
        acc = (acc shr 32) + c[2] + c[10] + c[11] - c[13] - c[14] - c[15]
        out[2] = acc.toInt()
        acc = (acc shr 32) + c[3] + 2 * c[11] + 2 * c[12] + c[13] - c[15] - c[8] - c[9]
        out[3] = acc.toInt()
        acc = (acc shr 32) + c[4] + 2 * c[12] + 2 * c[13] + c[14] - c[9] - c[10]
        out[4] = acc.toInt()
        acc = (acc shr 32) + c[5] + 2 * c[13] + 2 * c[14] + c[15] - c[10] - c[11]
        out[5] = acc.toInt()
        acc = (acc shr 32) + c[6] + 3 * c[14] + 2 * c[15] + c[13] - c[8] - c[9]
        out[6] = acc.toInt()
        acc = (acc shr 32) + c[7] + 3 * c[15] + c[8] - c[10] - c[11] - c[12] - c[13]
        out[7] = acc.toInt()
        val top = acc shr 32 // signed multiple of 2^256 remaining, in [-4, 6]

        // Subtract top * p in one pass, without branching on top. Since p = 2^256 - c with
        // c = 2^224 - 2^192 - 2^96 + 1, value - top * p = out + top * c. That lands within 6 * 2^224 of [0, 2^256), so
        // one masked add or subtract of p and a final reduction finish the job.
        var fold = 0L
        for (i in 0 until LIMBS) {
            fold += out[i].u() + top * C_SIGNS[i]
            out[i] = fold.toInt()
            fold = fold shr 32
        }
        fold += addMasked(out, out, P, (fold shr 63).toInt().opaque())
        subtractMasked(out, out, P, ((-fold) shr 63).toInt().opaque())
        reduceWithCarry(out, 0, P)
        return out
    }

    // c = 2^256 - p per 32-bit limb: +1 at limbs 0 and 7, -1 at limbs 3 and 6
    private val C_SIGNS = longArrayOf(1, 0, 0, -1, 0, 0, -1, 1)
}

private val Fp = SolinasP256Field

/** The scalar field mod n (Montgomery form) */
internal val p256ScalarField = MontgomeryField(N)

private val Fn = p256ScalarField

/**
 * Scalar inverse a^-1 mod n of a plain (non-Montgomery) value via Bernstein-Yang "safegcd" divsteps; maps 0 to 0. A
 * port of libsecp256k1's constant-time `secp256k1_modinv32` (MIT, Peter Dettman): 20 batches of 30 branch-free
 * divsteps on 9-limb signed 30-bit values. The source is written branch-free, but Kotlin compilers don't promise
 * constant-time output.
 */
internal fun invertModN(a: IntArray): IntArray {
    val d = IntArray(S30_LIMBS)
    val e = IntArray(S30_LIMBS).apply { this[0] = 1 }
    val f = N_S30.copyOf()
    val g = a.toSigned30()
    val t = IntArray(4)
    var zeta = -1 // -(delta + 1/2), delta starts at 1/2

    // 20 batches = 600 divsteps; 590 suffice for 256-bit inputs
    repeat(20) {
        zeta = divsteps30(zeta, f[0], g[0], t)
        updateDe30(d, e, t)
        updateFg30(f, g, t)
    }

    // g is now 0 and f is +/-1, so d is +/- the inverse
    normalize30(d, f[S30_LIMBS - 1])
    val inverse = d.fromSigned30()
    for (secret in arrayOf(d, e, f, g, t)) secret.fill(0)
    return inverse
}

private const val S30_LIMBS = 9
private const val M30 = 0x3fffffff

private val N_S30: IntArray = N.toSigned30()

/** n^-1 mod 2^30 */
private val N_INV30: Int = run {
    var inv = 1
    repeat(5) { inv *= 2 - N[0] * inv }
    inv and M30
}

/** 8 x 32-bit limbs -> 9 x 30-bit limbs (non-negative) */
private fun IntArray.toSigned30(): IntArray {
    val out = IntArray(S30_LIMBS)
    for (i in 0 until S30_LIMBS) {
        val bit = 30 * i
        val limb = bit / 32
        val shift = bit % 32
        var v = this[limb].u() ushr shift
        if (limb + 1 < LIMBS) v = v or (this[limb + 1].u() shl (32 - shift))
        out[i] = v.toInt() and M30
    }
    return out
}

/** 9 x 30-bit normalized limbs -> 8 x 32-bit limbs */
private fun IntArray.fromSigned30(): IntArray {
    val out = IntArray(LIMBS)
    for (i in 0 until S30_LIMBS) {
        val v = this[i].u()
        val bit = 30 * i
        val limb = bit / 32
        val shift = bit % 32
        out[limb] = out[limb] or (v shl shift).toInt()
        if (limb + 1 < LIMBS && shift > 2) out[limb + 1] = out[limb + 1] or (v ushr (32 - shift)).toInt()
    }
    return out
}

/** 30 branch-free divsteps on the low limbs of f and g. Writes the transition matrix [u, v, q, r] to [t]. */
private fun divsteps30(zetaIn: Int, f0: Int, g0: Int, t: IntArray): Int {
    var zeta = zetaIn
    var u = 1
    var v = 0
    var q = 0
    var r = 1
    var f = f0
    var g = g0
    val zero = opaqueZero.value
    repeat(30) {
        var mask1 = (zeta shr 31).opaque(zero) // zeta < 0
        val mask2 = (-(g and 1)).opaque(zero) // g odd
        val x = (f xor mask1) - mask1
        val y = (u xor mask1) - mask1
        val z = (v xor mask1) - mask1
        g += x and mask2
        q += y and mask2
        r += z and mask2
        mask1 = mask1 and mask2
        zeta = (zeta xor mask1) - 1
        f += g and mask1
        u += q and mask1
        v += r and mask1
        g = g ushr 1
        u = u shl 1
        v = v shl 1
    }
    t[0] = u
    t[1] = v
    t[2] = q
    t[3] = r
    return zeta
}

/** [d, e] = (t / 2^30) * [d, e] mod n, keeping both in (-2n, n) */
private fun updateDe30(d: IntArray, e: IntArray, t: IntArray) {
    val u = t[0].toLong()
    val v = t[1].toLong()
    val q = t[2].toLong()
    val r = t[3].toLong()
    val m = N_S30

    // Add n * [md, me] so the bottom 30 bits cancel; [u, q] if d < 0 and [v, r] if e < 0 keep the result in range
    val sd = (d[S30_LIMBS - 1] shr 31).opaque()
    val se = (e[S30_LIMBS - 1] shr 31).opaque()
    var md = (t[0] and sd) + (t[1] and se)
    var me = (t[2] and sd) + (t[3] and se)

    var cd = u * d[0] + v * e[0]
    var ce = q * d[0] + r * e[0]
    md -= (N_INV30 * cd.toInt() + md) and M30
    me -= (N_INV30 * ce.toInt() + me) and M30
    cd += m[0].toLong() * md
    ce += m[0].toLong() * me
    cd = cd shr 30
    ce = ce shr 30

    for (i in 1 until S30_LIMBS) {
        val di = d[i].toLong()
        val ei = e[i].toLong()
        cd += u * di + v * ei + m[i].toLong() * md
        ce += q * di + r * ei + m[i].toLong() * me
        d[i - 1] = cd.toInt() and M30
        e[i - 1] = ce.toInt() and M30
        cd = cd shr 30
        ce = ce shr 30
    }
    d[S30_LIMBS - 1] = cd.toInt()
    e[S30_LIMBS - 1] = ce.toInt()
}

/** [f, g] = (t / 2^30) * [f, g] */
private fun updateFg30(f: IntArray, g: IntArray, t: IntArray) {
    val u = t[0].toLong()
    val v = t[1].toLong()
    val q = t[2].toLong()
    val r = t[3].toLong()

    var cf = (u * f[0] + v * g[0]) shr 30
    var cg = (q * f[0] + r * g[0]) shr 30
    for (i in 1 until S30_LIMBS) {
        val fi = f[i].toLong()
        val gi = g[i].toLong()
        cf += u * fi + v * gi
        cg += q * fi + r * gi
        f[i - 1] = cf.toInt() and M30
        g[i - 1] = cg.toInt() and M30
        cf = cf shr 30
        cg = cg shr 30
    }
    f[S30_LIMBS - 1] = cf.toInt()
    g[S30_LIMBS - 1] = cg.toInt()
}

/** Brings r from (-2n, n) to [0, n), negating it first if [sign] < 0. Output limbs are in [0, 2^30). */
private fun normalize30(r: IntArray, sign: Int) {
    val m = N_S30

    var condAdd = (r[S30_LIMBS - 1] shr 31).opaque()
    for (i in 0 until S30_LIMBS) r[i] += m[i] and condAdd
    val condNegate = (sign shr 31).opaque()
    for (i in 0 until S30_LIMBS) r[i] = (r[i] xor condNegate) - condNegate
    propagate30(r)

    condAdd = (r[S30_LIMBS - 1] shr 31).opaque()
    for (i in 0 until S30_LIMBS) r[i] += m[i] and condAdd
    propagate30(r)
}

private fun propagate30(r: IntArray) {
    for (i in 0 until S30_LIMBS - 1) {
        r[i + 1] += r[i] shr 30
        r[i] = r[i] and M30
    }
}

/** Jacobian point (X/Z^2, Y/Z^3) with plain coordinates over Fp; Z = 0 is the point at infinity */
private class JacobianPoint(val x: IntArray, val y: IntArray, val z: IntArray) {
    val isInfinity: Boolean get() = z.isZero()
}

// Each point gets its own coordinate arrays, so nothing aliases Fp.one
private val INFINITY = JacobianPoint(Fp.one.copyOf(), Fp.one.copyOf(), IntArray(LIMBS))

private val G = JacobianPoint(
    hexLimbs("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"),
    hexLimbs("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5"),
    Fp.one.copyOf(),
)

/**
 * Fixed-base table: for nibble position i and value w in 1..15, entry w - 1 of row i holds w * 16^i * G in affine
 * form, flattened into [x] / [y] (8 limbs per entry) for the constant-time row scan.
 */
private class BaseTable(val x: Array<IntArray>, val y: Array<IntArray>)

/**
 * Computed lazily on first use from public values only, so it needn't be constant-time. All 960 points are converted
 * to affine with one shared field inversion (Montgomery's batch-inversion trick) instead of one inversion each.
 */
private val BASE_TABLE: BaseTable by lazy {
    val points = ArrayList<JacobianPoint>(64 * 15)
    var base = G // 16^i * G
    repeat(64) {
        var multiple = base
        points.add(multiple)
        repeat(14) {
            multiple = add(multiple, base)
            points.add(multiple)
        }
        base = double(double(double(double(base))))
    }

    // prefix[i] = z_0 * ... * z_i; walking back from the inverse of the full product yields each 1 / z_i
    val prefix = arrayOfNulls<IntArray>(points.size)
    var product = Fp.one
    for (i in points.indices) {
        product = Fp.multiply(product, points[i].z)
        prefix[i] = product
    }
    var inverse = Fp.invert(product) // 1 / (z_0 * ... * z_i), for i counting down

    val x = Array(64) { IntArray(15 * LIMBS) }
    val y = Array(64) { IntArray(15 * LIMBS) }
    for (i in points.indices.reversed()) {
        val point = points[i]
        val zInv = if (i == 0) inverse else Fp.multiply(inverse, prefix[i - 1]!!)
        inverse = Fp.multiply(inverse, point.z)
        val zInv2 = Fp.square(zInv)
        Fp.multiply(point.x, zInv2).copyInto(x[i / 15], (i % 15) * LIMBS)
        Fp.multiply(point.y, Fp.multiply(zInv2, zInv)).copyInto(y[i / 15], (i % 15) * LIMBS)
    }
    BaseTable(x, y)
}

private fun twice(a: IntArray): IntArray = Fp.add(a, a)

/** dbl-2001-b, specialized for curve parameter a = -3 */
private fun double(p: JacobianPoint): JacobianPoint {
    if (p.isInfinity) return p
    val delta = Fp.square(p.z)
    val gamma = Fp.square(p.y)
    val beta = Fp.multiply(p.x, gamma)
    val t = Fp.multiply(Fp.subtract(p.x, delta), Fp.add(p.x, delta))
    val alpha = Fp.add(t, twice(t))
    val beta4 = twice(twice(beta))
    val x3 = Fp.subtract(Fp.square(alpha), twice(beta4))
    val z3 = Fp.subtract(Fp.subtract(Fp.square(Fp.add(p.y, p.z)), gamma), delta)
    val y3 = Fp.subtract(Fp.multiply(alpha, Fp.subtract(beta4, x3)), twice(twice(twice(Fp.square(gamma)))))
    return JacobianPoint(x3, y3, z3)
}

/** add-2007-bl (only used to build the fixed-base table) */
private fun add(p: JacobianPoint, q: JacobianPoint): JacobianPoint {
    if (p.isInfinity) return q
    if (q.isInfinity) return p
    val z1z1 = Fp.square(p.z)
    val z2z2 = Fp.square(q.z)
    val u1 = Fp.multiply(p.x, z2z2)
    val u2 = Fp.multiply(q.x, z1z1)
    val s1 = Fp.multiply(Fp.multiply(p.y, q.z), z2z2)
    val s2 = Fp.multiply(Fp.multiply(q.y, p.z), z1z1)
    val h = Fp.subtract(u2, u1)
    val rHalf = Fp.subtract(s2, s1)
    if (h.isZero()) return if (rHalf.isZero()) double(p) else INFINITY

    val i = Fp.square(twice(h))
    val j = Fp.multiply(h, i)
    val r = twice(rHalf)
    val v = Fp.multiply(u1, i)
    val x3 = Fp.subtract(Fp.subtract(Fp.square(r), j), twice(v))
    val y3 = Fp.subtract(Fp.multiply(r, Fp.subtract(v, x3)), twice(Fp.multiply(s1, j)))
    val z3 = Fp.multiply(Fp.subtract(Fp.subtract(Fp.square(Fp.add(p.z, q.z)), z1z1), z2z2), h)
    return JacobianPoint(x3, y3, z3)
}

/** Affine x-coordinate (normal form) of k * G */
internal fun baseMultiplyAffineX(k: IntArray): IntArray {
    // k * G = sum over nibble positions i of k_i * 16^i * G: no doublings, one mixed addition per nibble. Every nibble
    // reads the whole table row and does the addition; a zero nibble then keeps the old accumulator.
    val table = BASE_TABLE
    val tableX = table.x
    val tableY = table.y
    val accX = IntArray(LIMBS)
    val accY = Fp.one.copyOf()
    val accZ = IntArray(LIMBS) // (0 : 1 : 0) is the point at infinity
    val qx = IntArray(LIMBS)
    val qy = IntArray(LIMBS)
    val zero = opaqueZero.value

    for (position in 0 until 64) {
        val w = (k[position ushr 3] ushr ((position and 7) * 4)) and 0xf
        val rowX = tableX[position]
        val rowY = tableY[position]
        qx.fill(0)
        qy.fill(0)
        for (entry in 0 until 15) {
            val select = eqMask(w, entry + 1, zero)
            val base = entry * LIMBS
            for (i in 0 until LIMBS) {
                qx[i] = qx[i] or (rowX[base + i] and select)
                qy[i] = qy[i] or (rowY[base + i] and select)
            }
        }

        val sum = addMixedComplete(accX, accY, accZ, qx, qy)
        val nonzero = eqMask(w, 0, zero).inv()
        conditionalAssign(accX, sum.x, nonzero)
        conditionalAssign(accY, sum.y, nonzero)
        conditionalAssign(accZ, sum.z, nonzero)
        sum.x.fill(0)
        sum.y.fill(0)
        sum.z.fill(0)
    }

    check(!accZ.isZero()) { "k * G is the point at infinity" }
    val zInv = Fp.invert(accZ)
    val x = Fp.multiply(accX, zInv)
    for (secret in arrayOf(accX, accY, accZ, qx, qy, zInv)) secret.fill(0)
    return x
}

/** Projective point (X/Z, Y/Z) with plain coordinates over Fp */
private class ProjectivePoint(val x: IntArray, val y: IntArray, val z: IntArray)

private val B = hexLimbs("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b")

/**
 * Complete mixed addition (X1 : Y1 : Z1) + (x2, y2) for a = -3: Renes-Costello-Batina 2015, Algorithm 5. It has no
 * special cases: it is correct for any projective input, including the point at infinity and doubling, as long as the
 * affine input is a curve point.
 */
private fun addMixedComplete(x1: IntArray, y1: IntArray, z1: IntArray, x2: IntArray, y2: IntArray): ProjectivePoint {
    val xx = Fp.multiply(x1, x2)
    val yy = Fp.multiply(y1, y2)
    val xyPairs = Fp.subtract(Fp.multiply(Fp.add(x1, y1), Fp.add(x2, y2)), Fp.add(xx, yy))
    val yzPairs = Fp.add(Fp.multiply(y2, z1), y1)
    val xzPairs = Fp.add(Fp.multiply(x2, z1), x1)
    val bzPart = Fp.subtract(xzPairs, Fp.multiply(B, z1))
    val bz3Part = Fp.add(twice(bzPart), bzPart)
    val yyMinusBz3 = Fp.subtract(yy, bz3Part)
    val yyPlusBz3 = Fp.add(yy, bz3Part)
    val z3 = Fp.add(twice(z1), z1)
    val bxzPart = Fp.subtract(Fp.multiply(B, xzPairs), Fp.add(z3, xx))
    val bxz3Part = Fp.add(twice(bxzPart), bxzPart)
    val xx3MinusZ3 = Fp.subtract(Fp.add(twice(xx), xx), z3)
    return ProjectivePoint(
        Fp.subtract(Fp.multiply(yyPlusBz3, xyPairs), Fp.multiply(yzPairs, bxz3Part)),
        Fp.add(Fp.multiply(yyPlusBz3, yyMinusBz3), Fp.multiply(xx3MinusZ3, bxz3Part)),
        Fp.add(Fp.multiply(yyMinusBz3, yzPairs), Fp.multiply(xyPairs, xx3MinusZ3)),
    )
}

/**
 * RFC 6979 section 3.2 nonce generation with HMAC-SHA-256 (qlen = hlen = 256). [mac] computes HMAC(key, first length
 * bytes of message); tests replace it to force candidate rejection.
 */
internal class Rfc6979Nonces(
    x: ByteArray,
    h: ByteArray,
    private val mac: (key: ByteArray, message: ByteArray, length: Int) -> ByteArray = ::wipingHmacSha256,
) {
    private var k = ByteArray(32)
    private var v = ByteArray(32) { 1 }
    private var first = true

    // V || tag || x || h, built in place so no intermediate copy holds the private key. Zeroed after init.
    private val seed = ByteArray(32 + 1 + 32 + 32)

    init {
        x.copyInto(seed, 33)
        h.copyInto(seed, 65)
        rekey(0x00, seed.size)
        rekey(0x01, seed.size)
        seed.fill(0)
    }

    fun next(): IntArray {
        while (true) {
            if (!first) rekey(0x00, 33)
            first = false
            setV(mac(k, v, v.size))
            val candidate = v.toLimbs()
            // Branches on the secret candidate; see pureEcdsaSecp256r1 for why that is accepted
            if (!candidate.isZero() && !geq(candidate, N)) return candidate
            candidate.fill(0)
        }
    }

    /** Zeroes the generator's state; it can't be used afterwards */
    fun wipe() {
        k.fill(0)
        v.fill(0)
        seed.fill(0)
    }

    /** K = HMAC_K(V || [tag] || rest of [seed] up to [length]), then V = HMAC_K(V) */
    private fun rekey(tag: Int, length: Int) {
        v.copyInto(seed)
        seed[32] = tag.toByte()
        setK(mac(k, seed, length))
        setV(mac(k, v, v.size))
    }

    private fun setK(value: ByteArray) {
        k.fill(0)
        k = value
    }

    private fun setV(value: ByteArray) {
        v.fill(0)
        v = value
    }
}

/** HMAC-SHA-256 of the first [length] bytes of [message], zeroing its own key-derived state afterwards */
private fun wipingHmacSha256(key: ByteArray, message: ByteArray, length: Int): ByteArray {
    val hmac = PureHmac(key, PureSha256())
    try {
        return hmac.mac(message, length)
    } finally {
        hmac.wipe()
    }
}
