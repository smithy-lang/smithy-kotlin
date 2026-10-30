/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

private fun pattern(size: Int) = ByteArray(size) { (it * 31 + 7).toByte() }

/**
 * Deterministic (RFC 6979) P-256/SHA-256 signatures of [pattern] messages, computed with an independent Python big-int
 * implementation (checked against RFC 6979 A.2.5) and each verified with Python `cryptography`.
 */
private val signatureVectors = listOf(
    Triple("0000000000000000000000000000000000000000000000000000000000000001", 0, "304502200e69cb117664cedd6734e80151eabd388de266533e163fbe8ea463065c0fd71a022100c284e67e0baf2cf8e07896a5c5f222cad11e35c997c18c59d79bdbad6881783c"),
    Triple("0000000000000000000000000000000000000000000000000000000000000001", 1, "3046022100b11596014a100a8a2ed0229e94db0668c3d0acc83c228d7690104049ea037162022100bb4b244b0c617cf17b04fec6a70aaab0bcba1c21f3e9bd931cc479c71410c65f"),
    Triple("0000000000000000000000000000000000000000000000000000000000000001", 55, "3046022100b14e4ed095c7528fd5bd9192bfff9ff491db0bbaf7d2059037a430e3b4c8db25022100bde4f0062ae6973ddfb2bc6a31dcf24a89e5d9f6f9ba34f256a9e6a5758329f2"),
    Triple("0000000000000000000000000000000000000000000000000000000000000001", 64, "3045022100cef1dac045a2455fb7b4c646284961eaa1419871b212c61bf7180aba052a995202201b8cac1c16e747af82eff53bc26eb154f385eaa75ebf318f3f5f289fab45c7f2"),
    Triple("0000000000000000000000000000000000000000000000000000000000000001", 300, "304502201584093b1c471a0bea592e2b8030b6371d4b287afb651a16e0ef38ac5364e4a8022100a662f1e70ad9a6b6ff48ee5980c1f146b60c9f802dffcff2ed789f40557d0592"),
    Triple("0000000000000000000000000000000000000000000000000000000000000001", 100_003, "3046022100a3d9e66eb8f65e3c23f7b767e40adb4521dfef1b25bdb0ff6511350e812e2877022100b3cf01e929659733460e3bf4fbc8a64059728296698ba0bba09929b965af71d1"),
    Triple("0000000000000000000000000000000000000000000000000000000000000002", 0, "30440220455f5d50d851d716fd28a4b7d8dad4af0a99446069ec542f3b70a19b8e1df430022065f1001398a9aae8336ad599c9fabb6e5487c37681c6aa0c0058180437ed9097"),
    Triple("0000000000000000000000000000000000000000000000000000000000000002", 1, "304402200084464bf89968763dff57df787660fe6658e1222d7f4d5cae104b7c01fcd3d202200dd76adb0e20b9a54ffbc4d487718a7d45dfff04455fc0c69102e8aba845ae4a"),
    Triple("0000000000000000000000000000000000000000000000000000000000000002", 55, "304402202adea448ebd32812dba874f9368358533326aeecaed8dcb30cce23b8318c309202205b251f9c5093e00d0b8719b2565edef830c947c0ea35efeebcad120242b3183e"),
    Triple("0000000000000000000000000000000000000000000000000000000000000002", 64, "3046022100f5e525a783b4b4d42ac7c88e4f1350e922e09777c351debbcc80dda18b68c5da022100dc9db2107e1a26c5ef6c23398e1194ea7a4c759bc3a6f77400443ede3029dc2c"),
    Triple("0000000000000000000000000000000000000000000000000000000000000002", 300, "3046022100cb325726c12506fc30e2f0369339f60c73a019167b2cde093cddc90b7f26c052022100acf9949db81d8a4d9d0e37fd39b9bf8aa139d74c555629d046c057bedfa9a523"),
    Triple("0000000000000000000000000000000000000000000000000000000000000002", 100_003, "3045022100ef420fa5fdf5eccdaf9999573abc751440009fef29c6ffea0f097665943fafd0022062205c160967cf593acde5cc768812b6857eb94f12d63c8b0c35c79f4d715463"),
    // n - 1
    Triple("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550", 0, "30460221009a4460ec5a9484c194d61987c2d9bddb2ada0e708fdf51ae0fe5974d8580012d022100c3f2dbedc40a96ef521afd115ad45c3043e30d612fcdd411ec3833abd6715b4d"),
    Triple("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550", 1, "3045022100a1daa435db724ea0d5da888920c3a378eb291ced3386c491addcd53ddfb6668902205e344c9ba33fc39aef3ef3eb44da075d6779194ac83b3d2d3e6051de640605a2"),
    Triple("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550", 55, "3044022009af0efde0661446e30a8c425a120e5df3f4c5ffdc932887f9c2a0cde303d3df022041e3ab09e3b5f21ce332f9b87f8f63cc6dd82ca36a6de44fcebef05ea44092a8"),
    Triple("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550", 64, "3045022100b5be0d01f228b006ac3ac693c430e6bb56910949ada20b5acf99e8239de6ca5c02203cbce0b3e1cf3ee612b6dd914a5a2d821e1d0585cc3288db455c0c22cf3b2c13"),
    Triple("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550", 300, "304502200fb3ef807408ba9a00c4051781a9c2cafaffca772de5b3084e9d45bebcf46852022100ec44b743594f867ceb3af98874bf3932a3175b28f294b7a87fb3cc8f76718e03"),
    Triple("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550", 100_003, "3046022100afd35f4c5a9382a2e9de02ac1f9225519e42b928ce27d439b8cadae7f2c4aaea0221009d440266c7e9336f31b45b7575df6916ad63462997bbf44a4f0caf9ce51438cd"),
    Triple("00000000000000000000000000000000000000000000000000000000deadbeef", 0, "304602210080979b6bb5da6592f0251048f8a7c6ec71b652b070424c3a1bbd61f65788304a022100c73045c5d6e810255fadef5a4b17beb8297ffb5c237e18c500d7a3fd65bfd160"),
    Triple("00000000000000000000000000000000000000000000000000000000deadbeef", 1, "304502204d1ad77e7e990dffb259a2dc2289837fa9ce25e5998dfbe8d485bb20b7fa9081022100e5742875656a99811b409f1b227d664acfe55e5857efda5271a7a6e3c8f2f7f9"),
    Triple("00000000000000000000000000000000000000000000000000000000deadbeef", 55, "30440220547d7be926ce293a11876154f6c02281a52bd73c913469582618879960ad728d022056e1e0b9e2d6cfcd218910459d7300d348b154319117a79cfae44fb81f792d04"),
    Triple("00000000000000000000000000000000000000000000000000000000deadbeef", 64, "3046022100949153f1804e958037ec69418441844dd263b62355cf4de9b01c4aa0513beeb9022100d10022c3e3582c1a1220f1fedbcbb02e8339400ff98b1cc9f6697f978c246ab9"),
    Triple("00000000000000000000000000000000000000000000000000000000deadbeef", 300, "3044022039da9a6df4c719d0f94fb67af3ad058d30798efb7533a6ff0e75d5d7bd5678c5022064f3fca49b15606b0a10be952a403984cc4aff1cb3d8001e426da25cda6d2472"),
    Triple("00000000000000000000000000000000000000000000000000000000deadbeef", 100_003, "3045022029a348b78315fcacda8acd2be66a5bc435d818a6cde7231d77728af2939da8fd022100c8cb16a840bb88439563cd2b48e2ea3b24790a09e3f1277d5abac4a8f37faaf9"),
    // top bit set
    Triple("f10f781241e2246678b6b45c857069208152a53863e47fac33f607ab405006f4", 0, "3044022005e469f931d899b8d6809dc01ce125b3598aa5636f19b04b4fbb6c3806a448200220243314ac0ec2d69a896e1d8ee18f934c130ddc411018eb4480ebd875221e117c"),
    Triple("f10f781241e2246678b6b45c857069208152a53863e47fac33f607ab405006f4", 1, "3045022100a9c7c0e237bb25bbf0bea954f31df30ef436d938f2548439b2ce9ef765e0201f022079dc700925f744728c8ae22018d8a77d8bc7fcb675956224d90a80136d16f451"),
    Triple("f10f781241e2246678b6b45c857069208152a53863e47fac33f607ab405006f4", 55, "304402201165b1bf92928416781e0fa6cb5a200a709dbf3d3c898060bdf377adac7055e302207acac435bb3e3c65eb1dc19e4045506a6bd1b40450c0c768d638ab764e080af7"),
    Triple("f10f781241e2246678b6b45c857069208152a53863e47fac33f607ab405006f4", 64, "304502210081055efaa9d4b88fa39dcde930d555907ba0c04adb80b9fe594def7acd8e5d79022065223ae1a9e2b21a47a8943cf1ace2b63429c137c49a1992b4f46bb3c854e201"),
    Triple("f10f781241e2246678b6b45c857069208152a53863e47fac33f607ab405006f4", 300, "304502207bfa70503db61bc2cf4c4b0bc97de8268df4f3948b87281539afcaf8ae1a597002210085210161bad26fae9529cfb2b906698775175793bd1aa46ca4f5710ed772a26e"),
    Triple("f10f781241e2246678b6b45c857069208152a53863e47fac33f607ab405006f4", 100_003, "304502204398a8d4a90fe61672104370320499f6d6a9503813172c75cf3ee7dc8b9c8712022100831a3e4c731fd6cd5226f969f6c98bf7e17866078dc159fefb522e14771dac30"),
    Triple("51829a529d91aa39bbeee65e8d642d4542609eda1a49e0b72ad355e3d875d31a", 0, "30450221009a0fc03365da658bb6a5351d82a0f48fc536fdb2e950acb69d0d327089ceebbd02203bdae695a37bd2996a6a60291f03b9b477890c4930dd805af3f582d7dddfd873"),
    Triple("51829a529d91aa39bbeee65e8d642d4542609eda1a49e0b72ad355e3d875d31a", 1, "3045022100dc409b6083ed72b1eedcc765e8e58c623cc8d412f2ab8183cacbe7eb2012542c022058f4952dbed521297c4b734e68a5f3fb965e2b9f641f8e3f90dd1f4ce87469f7"),
    Triple("51829a529d91aa39bbeee65e8d642d4542609eda1a49e0b72ad355e3d875d31a", 55, "304402206efc8b154f31288654e815f1be7878b87ed681333f2cfdf078fd4980cfa8018c02206857e7d09103c0265cada3763ec3a5b81753bcc62873dd94c72d3a51b90ae445"),
    Triple("51829a529d91aa39bbeee65e8d642d4542609eda1a49e0b72ad355e3d875d31a", 64, "3046022100c9533497bbbd5e7fdedbdab53ab3138a89ace26cab05d84c7d8b0bf3c1d1d874022100a8395a3b68023df2a64f9eacb2b0ca4ed26876c76fa1096dd1bb1ef3154e859c"),
    Triple("51829a529d91aa39bbeee65e8d642d4542609eda1a49e0b72ad355e3d875d31a", 300, "304402202dd9fdbf84cff0b6dbb22dd3b552ebf42c8a2df551c55b0b517e14bc886c75ac022060177dd1fa25f61413a9d9456cf6ba3145913344cfd2e0ec958134fc4ea43b33"),
    Triple("51829a529d91aa39bbeee65e8d642d4542609eda1a49e0b72ad355e3d875d31a", 100_003, "30460221008271cfda13c7066697451747b9ca7727e3042edade5027a826c9f2e87a48803b022100e23a6a22536a6df1ee0ffd0ff9be326132ceae54114b01775a606b0f7a96463f"),
)

/** Checks the optimized [pureEcdsaSecp256r1] and its building blocks against independently computed values */
@OptIn(ExperimentalStdlibApi::class)
class PureEcdsaOptimizedTest {
    private val p = hexLimbs("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")
    private val n = hexLimbs("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")

    @Test
    fun testKnownSignatures() {
        for ((key, size, expected) in signatureVectors) {
            assertEquals(expected, pureEcdsaSecp256r1(key.hexToByteArray(), pattern(size)).toHexString(), "key=$key, size=$size")
        }
    }

    @Test
    fun testSignedBigIntegerKeyWithTopBitSet() {
        val (key, size, expected) = signatureVectors.first { it.first.startsWith("f1") }
        assertEquals(expected, pureEcdsaSecp256r1(byteArrayOf(0) + key.hexToByteArray(), pattern(size)).toHexString())
    }

    @Test
    fun testRejectsKeysAtOrAboveN() {
        val message = pattern(10)
        for (key in listOf(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", // n
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632552", // n + 1
            "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        )) {
            assertFails("key=$key") { pureEcdsaSecp256r1(key.hexToByteArray(), message) }
        }
    }

    /** Plain shift-and-add a * b mod p, independent of the Solinas and Montgomery code under test */
    private fun referenceMultiply(a: IntArray, b: IntArray): IntArray {
        var acc = IntArray(8)
        for (bit in 255 downTo 0) {
            acc = addModP(acc, acc)
            if ((b[bit ushr 5] ushr (bit and 31)) and 1 == 1) acc = addModP(acc, a)
        }
        return acc
    }

    /** (a + b) mod p for a, b < p */
    private fun addModP(a: IntArray, b: IntArray): IntArray {
        val sum = IntArray(8)
        var carry = 0L
        for (i in 0 until 8) {
            val s = a[i].toUInt().toLong() + b[i].toUInt().toLong() + carry
            sum[i] = s.toInt()
            carry = s ushr 32
        }
        if (carry == 0L && lessThan(sum, p)) return sum
        var borrow = 0L
        for (i in 0 until 8) {
            val d = sum[i].toUInt().toLong() - p[i].toUInt().toLong() - borrow
            sum[i] = d.toInt()
            borrow = (d ushr 63)
        }
        return sum
    }

    private fun fieldValues(): List<IntArray> {
        val random = Random(256)
        val edges = listOf(
            IntArray(8),
            IntArray(8).apply { this[0] = 1 },
            IntArray(8).apply { this[0] = 2 },
            p.copyOf().apply { this[0] -= 1 },
            p.copyOf().apply { this[0] -= 2 },
        )
        return edges + List(200) { randomBelow(random, p) }
    }

    @Test
    fun testSolinasFieldMatchesReference() {
        val values = fieldValues()
        val random = Random(7)
        for (a in values) {
            assertContentEquals(referenceMultiply(a, a), SolinasP256Field.square(a), "square ${a.toList()}")
            val b = values[random.nextInt(values.size)]
            assertContentEquals(referenceMultiply(a, b), SolinasP256Field.multiply(a, b), "multiply ${a.toList()} ${b.toList()}")
            for (edge in values.take(5)) {
                assertContentEquals(referenceMultiply(a, edge), SolinasP256Field.multiply(a, edge), "multiply ${a.toList()} by edge")
            }
        }
    }

    @Test
    fun testSolinasInverseIsInverse() {
        // multiply is checked against the reference above, so a * a^-1 == 1 pins down invert
        for (a in fieldValues().drop(1)) {
            assertContentEquals(SolinasP256Field.one, SolinasP256Field.multiply(a, SolinasP256Field.invert(a)), "a = ${a.toList()}")
        }
        assertContentEquals(IntArray(8), SolinasP256Field.invert(IntArray(8)))
    }

    @Test
    fun testScalarInverse() {
        val field = p256ScalarField
        val random = Random(2)
        val nMinusOne = n.copyOf().apply { this[0] -= 1 }
        val values = listOf(IntArray(8).apply { this[0] = 1 }, nMinusOne) + List(200) { randomBelow(random, n) }
        for (a in values) {
            assertTrue(a.any { it != 0 })
            val one = IntArray(8).apply { this[0] = 1 }
            assertContentEquals(one, field.multiply(field.toMontgomery(a), invertModN(a)), "a = ${a.toList()}")
        }
        assertContentEquals(IntArray(8), invertModN(IntArray(8)))
    }

    @Test
    fun testBaseMultiplyMatchesReference() {
        val random = Random(3)
        fun scalar(vararg limbs: Int) = IntArray(8).also { limbs.copyInto(it) }
        val nMinus = { d: Int -> n.copyOf().apply { this[0] -= d } }
        val scalars = listOf(
            scalar(1),
            scalar(2),
            scalar(15),
            scalar(16), // zero low nibble
            scalar(0, 0, 0, 0, 0, 0, 0, 1), // only the top limb set
            IntArray(8) { 0x0f0f0f0f }, // alternating zero nibbles
            IntArray(8) { 0x77777777 }, // every nibble the same
            nMinus(1),
            nMinus(2),
        ) + List(50) { randomBelow(random, n) } +
            // random scalars with about half their nibbles cleared
            List(50) { randomBelow(random, n).map { it and randomNibbleMask(random) }.toIntArray() }
                .filter { it.any { limb -> limb != 0 } }

        for (k in scalars) {
            assertContentEquals(referenceBaseMultiplyAffineX(k), baseMultiplyAffineX(k), "k = ${k.toList()}")
        }
    }

    /** Each nibble is 0xf or 0 with equal odds */
    private fun randomNibbleMask(random: Random): Int {
        var mask = 0
        repeat(8) { i -> if (random.nextBoolean()) mask = mask or (0xf shl (4 * i)) }
        return mask
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

    private fun hexLimbs(hex: String): IntArray {
        val bytes = hex.hexToByteArray()
        return IntArray(8) { bytes.getIntBe(28 - 4 * it) }
    }
}
