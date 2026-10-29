/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.hashing.pure

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private class NistVector(val message: String, val d: String, val k: String, val r: String, val s: String)

/**
 * NIST CAVP FIPS 186-3 SigGen vectors for [P-256,SHA-256], from pyca/cryptography's copy
 * (vectors/cryptography_vectors/asymmetric/ECDSA/FIPS_186-3/SigGen.txt). These fix the nonce, so they test the signing
 * arithmetic independently of RFC 6979. Wycheproof's ECDSA sets are verification tests and don't apply to a signer.
 */
private val nistSigGenVectors = listOf(
    NistVector("5905238877c77421f73e43ee3da6f2d9e2ccad5fc942dcec0cbd25482935faaf416983fe165b1a045ee2bcd2e6dca3bdf46c4310a7461f9a37960ca672d3feb5473e253605fb1ddfd28065b53cb5858a8ad28175bf9bd386a5e471ea7a65c17cc934a9d791e91491eb3754d03799790fe2d308d16146d5c9b0d0debd97d79ce8", "519b423d715f8b581f4fa8ee59f4771a5b44c8130b4e3eacca54a56dda72b464", "94a1bbb14b906a61a280f245f9e93c7f3b4a6247824f5d33b9670787642a68de", "f3ac8061b514795b8843e3d6629527ed2afd6b1f6a555a7acabb5e6f79c8c2ac", "8bf77819ca05a6b2786c76262bf7371cef97b218e96f175a3ccdda2acc058903"),
    NistVector("c35e2f092553c55772926bdbe87c9796827d17024dbb9233a545366e2e5987dd344deb72df987144b8c6c43bc41b654b94cc856e16b96d7a821c8ec039b503e3d86728c494a967d83011a0e090b5d54cd47f4e366c0912bc808fbb2ea96efac88fb3ebec9342738e225f7c7c2b011ce375b56621a20642b4d36e060db4524af1", "0f56db78ca460b055c500064824bed999a25aaf48ebb519ac201537b85479813", "6d3e71882c3b83b156bb14e0ab184aa9fb728068d3ae9fac421187ae0b2f34c6", "976d3a4e9d23326dc0baa9fa560b7c4e53f42864f508483a6473b6a11079b2db", "1b766e9ceb71ba6c01dcd46e0af462cd4cfa652ae5017d4555b8eeefe36e1932"),
    NistVector("3c054e333a94259c36af09ab5b4ff9beb3492f8d5b4282d16801daccb29f70fe61a0b37ffef5c04cd1b70e85b1f549a1c4dc672985e50f43ea037efa9964f096b5f62f7ffdf8d6bfb2cc859558f5a393cb949dbd48f269343b5263dcdb9c556eca074f2e98e6d94c2c29a677afaf806edf79b15a3fcd46e7067b7669f83188ee", "e283871239837e13b95f789e6e1af63bf61c918c992e62bca040d64cad1fc2ef", "ad5e887eb2b380b8d8280ad6e5ff8a60f4d26243e0124c2f31a297b5d0835de2", "35fb60f5ca0f3ca08542fb3cc641c8263a2cab7a90ee6a5e1583fac2bb6f6bd1", "ee59d81bc9db1055cc0ed97b159d8784af04e98511d0a9a407b99bb292572e96"),
    NistVector("0989122410d522af64ceb07da2c865219046b4c3d9d99b01278c07ff63eaf1039cb787ae9e2dd46436cc0415f280c562bebb83a23e639e476a02ec8cff7ea06cd12c86dcc3adefbf1a9e9a9b6646c7599ec631b0da9a60debeb9b3e19324977f3b4f36892c8a38671c8e1cc8e50fcd50f9e51deaf98272f9266fc702e4e57c30", "a3d2d3b7596f6592ce98b4bfe10d41837f10027a90d7bb75349490018cf72d07", "24fc90e1da13f17ef9fe84cc96b9471ed1aaac17e3a4bae33a115df4e5834f18", "d7c562370af617b581c84a2468cc8bd50bb1cbf322de41b7887ce07c0e5884ca", "b46d9f2d8c4bf83546ff178f1d78937c008d64e8ecc5cbb825cb21d94d670d89"),
    NistVector("dc66e39f9bbfd9865318531ffe9207f934fa615a5b285708a5e9c46b7775150e818d7f24d2a123df3672fff2094e3fd3df6fbe259e3989dd5edfcccbe7d45e26a775a5c4329a084f057c42c13f3248e3fd6f0c76678f890f513c32292dd306eaa84a59abe34b16cb5e38d0e885525d10336ca443e1682aa04a7af832b0eee4e7", "53a0e8a8fe93db01e7ae94e1a9882a102ebd079b3a535827d583626c272d280d", "5d833e8d24cc7a402d7ee7ec852a3587cddeb48358cea71b0bedb8fabe84e0c4", "18caaf7b663507a8bcd992b836dec9dc5703c080af5e51dfa3a9a7c387182604", "77c68928ac3b88d985fb43fb615fb7ff45c18ba5c81af796c613dfa98352d29c"),
    NistVector("600974e7d8c5508e2c1aab0783ad0d7c4494ab2b4da265c2fe496421c4df238b0be25f25659157c8a225fb03953607f7df996acfd402f147e37aee2f1693e3bf1c35eab3ae360a2bd91d04622ea47f83d863d2dfecb618e8b8bdc39e17d15d672eee03bb4ce2cc5cf6b217e5faf3f336fdd87d972d3a8b8a593ba85955cc9d71", "4af107e8e2194c830ffb712a65511bc9186a133007855b49ab4b3833aefc4a1d", "e18f96f84dfa2fd3cdfaec9159d4c338cd54ad314134f0b31e20591fc238d0ab", "8524c5024e2d9a73bde8c72d9129f57873bbad0ed05215a372a84fdbc78f2e68", "d18c2caf3b1072f87064ec5e8953f51301cada03469c640244760328eb5a05cb"),
    NistVector("dfa6cb9b39adda6c74cc8b2a8b53a12c499ab9dee01b4123642b4f11af336a91a5c9ce0520eb2395a6190ecbf6169c4cba81941de8e76c9c908eb843b98ce95e0da29c5d4388040264e05e07030a577cc5d176387154eabae2af52a83e85c61c7c61da930c9b19e45d7e34c8516dc3c238fddd6e450a77455d534c48a152010b", "78dfaa09f1076850b3e206e477494cddcfb822aaa0128475053592c48ebaf4ab", "295544dbb2da3da170741c9b2c6551d40af7ed4e891445f11a02b66a5c258a77", "c5a186d72df452015480f7f338970bfe825087f05c0088d95305f87aacc9b254", "84a58f9e9d9e735344b316b1aa1ab5185665b85147dc82d92e969d7bee31ca30"),
    NistVector("51d2547cbff92431174aa7fc7302139519d98071c755ff1c92e4694b58587ea560f72f32fc6dd4dee7d22bb7387381d0256e2862d0644cdf2c277c5d740fa089830eb52bf79d1e75b8596ecf0ea58a0b9df61e0c9754bfcd62efab6ea1bd216bf181c5593da79f10135a9bc6e164f1854bc8859734341aad237ba29a81a3fc8b", "80e692e3eb9fcd8c7d44e7de9f7a5952686407f90025a1d87e52c7096a62618a", "7c80fd66d62cc076cef2d030c17c0a69c99611549cb32c4ff662475adbe84b22", "9d0c6afb6df3bced455b459cc21387e14929392664bb8741a3693a1795ca6902", "d7f9ddd191f1f412869429209ee3814c75c72fa46a9cccf804a2f5cc0b7e739f"),
    NistVector("558c2ac13026402bad4a0a83ebc9468e50f7ffab06d6f981e5db1d082098065bcff6f21a7a74558b1e8612914b8b5a0aa28ed5b574c36ac4ea5868432a62bb8ef0695d27c1e3ceaf75c7b251c65ddb268696f07c16d2767973d85beb443f211e6445e7fe5d46f0dce70d58a4cd9fe70688c035688ea8c6baec65a5fc7e2c93e8", "5e666c0db0214c3b627a8e48541cc84a8b6fd15f300da4dff5d18aec6c55b881", "2e7625a48874d86c9e467f890aaa7cd6ebdf71c0102bfdcfa24565d6af3fdce9", "2f9e2b4e9f747c657f705bffd124ee178bbc5391c86d056717b140c153570fd9", "f5413bfd85949da8d83de83ab0d19b2986613e224d1901d76919de23ccd03199"),
    NistVector("4d55c99ef6bd54621662c3d110c3cb627c03d6311393b264ab97b90a4b15214a5593ba2510a53d63fb34be251facb697c973e11b665cb7920f1684b0031b4dd370cb927ca7168b0bf8ad285e05e9e31e34bc24024739fdc10b78586f29eff94412034e3b606ed850ec2c1900e8e68151fc4aee5adebb066eb6da4eaa5681378e", "f73f455271c877c4d5334627e37c278f68d143014b0a05aa62f308b2101c5308", "62f8665fd6e26b3fa069e85281777a9b1f0dfd2c0b9f54a086d0c109ff9fd615", "1cc628533d0004b2b20e7f4baad0b8bb5e0673db159bbccf92491aef61fc9620", "880e0bbf82a8cf818ed46ba03cf0fc6c898e36fca36cc7fdb1d2db7503634430"),
    NistVector("f8248ad47d97c18c984f1f5c10950dc1404713c56b6ea397e01e6dd925e903b4fadfe2c9e877169e71ce3c7fe5ce70ee4255d9cdc26f6943bf48687874de64f6cf30a012512e787b88059bbf561162bdcc23a3742c835ac144cc14167b1bd6727e940540a9c99f3cbb41fb1dcb00d76dda04995847c657f4c19d303eb09eb48a", "b20d705d9bd7c2b8dc60393a5357f632990e599a0975573ac67fd89b49187906", "72b656f6b35b9ccbc712c9f1f3b1a14cbbebaec41c4bca8da18f492a062d6f6f", "9886ae46c1415c3bc959e82b760ad760aab66885a84e620aa339fdf102465c42", "2bf3a80bc04faa35ebecc0f4864ac02d349f6f126e0f988501b8d3075409a26c"),
    NistVector("3b6ee2425940b3d240d35b97b6dcd61ed3423d8e71a0ada35d47b322d17b35ea0472f35edd1d252f87b8b65ef4b716669fc9ac28b00d34a9d66ad118c9d94e7f46d0b4f6c2b2d339fd6bcd351241a387cc82609057048c12c4ec3d85c661975c45b300cb96930d89370a327c98b67defaa89497aa8ef994c77f1130f752f94a4", "d4234bebfbc821050341a37e1240efe5e33763cbbb2ef76a1c79e24724e5a5e7", "d926fe10f1bfd9855610f4f5a3d666b1a149344057e35537373372ead8b1a778", "490efd106be11fc365c7467eb89b8d39e15d65175356775deab211163c2504cb", "644300fc0da4d40fb8c6ead510d14f0bd4e1321a469e9c0a581464c7186b7aa7"),
    NistVector("c5204b81ec0a4df5b7e9fda3dc245f98082ae7f4efe81998dcaa286bd4507ca840a53d21b01e904f55e38f78c3757d5a5a4a44b1d5d4e480be3afb5b394a5d2840af42b1b4083d40afbfe22d702f370d32dbfd392e128ea4724d66a3701da41ae2f03bb4d91bb946c7969404cb544f71eb7a49eb4c4ec55799bda1eb545143a7", "b58f5211dff440626bb56d0ad483193d606cf21f36d9830543327292f4d25d8c", "e158bf4a2d19a99149d9cdb879294ccb7aaeae03d75ddd616ef8ae51a6dc1071", "e67a9717ccf96841489d6541f4f6adb12d17b59a6bef847b6183b8fcf16a32eb", "9ae6ba6d637706849a6a9fc388cf0232d85c26ea0d1fe7437adb48de58364333"),
    NistVector("72e81fe221fb402148d8b7ab03549f1180bcc03d41ca59d7653801f0ba853add1f6d29edd7f9abc621b2d548f8dbf8979bd16608d2d8fc3260b4ebc0dd42482481d548c7075711b5759649c41f439fad69954956c9326841ea6492956829f9e0dc789f73633b40f6ac77bcae6dfc7930cfe89e526d1684365c5b0be2437fdb01", "54c066711cdb061eda07e5275f7e95a9962c6764b84f6f1f3ab5a588e0a2afb1", "646fe933e96c3b8f9f507498e907fdd201f08478d0202c752a7c2cfebf4d061a", "b53ce4da1aa7c0dc77a1896ab716b921499aed78df725b1504aba1597ba0c64b", "d7c246dc7ad0e67700c373edcfdd1c0a0495fc954549ad579df6ed1438840851"),
    NistVector("21188c3edd5de088dacc1076b9e1bcecd79de1003c2414c3866173054dc82dde85169baa77993adb20c269f60a5226111828578bcc7c29e6e8d2dae81806152c8ba0c6ada1986a1983ebeec1473a73a04795b6319d48662d40881c1723a706f516fe75300f92408aa1dc6ae4288d2046f23c1aa2e54b7fb6448a0da922bd7f34", "34fa4682bf6cb5b16783adcd18f0e6879b92185f76d7c920409f904f522db4b1", "a6f463ee72c9492bc792fe98163112837aebd07bab7a84aaed05be64db3086f4", "542c40a18140a6266d6f0286e24e9a7bad7650e72ef0e2131e629c076d962663", "4f7f65305e24a6bbb5cff714ba8f5a2cee5bdc89ba8d75dcbf21966ce38eb66f"),
)

@OptIn(ExperimentalStdlibApi::class)
class PureEcdsaVectorsTest {
    @Test
    fun testNistSigGenVectors() {
        for (v in nistSigGenVectors) {
            val (r, s) = assertNotNull(signWithNonce(limbsOf(v.d), messageScalar(v.message.hexToByteArray()), limbsOf(v.k)))
            assertContentEquals(limbsOf(v.r), r, "r, d=${v.d}")
            assertContentEquals(limbsOf(v.s), s, "s, d=${v.d}")
        }
    }

    @Test
    fun testMessageHashAtLeastN() {
        // SHA-256(7f893a8700000000) = ffffffff683541c1... >= n, so h must be reduced mod n. Expected value from an
        // independent Python RFC 6979 signer.
        val key = byteArrayOf(0x12) + ByteArray(30) + byteArrayOf(0x01)
        assertEquals(
            "3045022006a46584dc9acf1a7574f0a79e902f16cca94b9b0345c55e7bdb41888b216ed9022100ae4783a260ee81ddabd2acbd9de86432a5ce2404eec2d34f583934e96ea47d85",
            pureEcdsaSecp256r1(key, "7f893a8700000000".hexToByteArray()).toHexString(),
        )
    }

    @Test
    fun testRfc6979NonceSequence() {
        // RFC 6979 A.2.5 key and message "sample". The first nonce is the one published there; the next two (from an
        // independent Python implementation) exercise the K/V update used after a rejected candidate.
        val x = "c9afa9d845ba75166b5c215767b1d6934e50c3db36e89b127b8a622b120f6721".hexToByteArray()
        val h = PureSha256().apply { update("sample".encodeToByteArray()) }.digest()
        val nonces = Rfc6979Nonces(x, h)
        for (expected in listOf(
            "a6e3c57dd01abe90086538398355dd4c3b17aa873382b0f24d6129493d8aad60",
            "8e83dc490bc5fc4d5992bd63cd87f254adffcb930f8a8011702a88870f638fdb",
            "7b8dc9ad8ce159abca1b9915fc1470e91d5ad2443b3032557e78f47e180ab702",
        )) {
            assertContentEquals(limbsOf(expected), nonces.next())
        }
    }

    @Test
    fun testRetriesWhenSIsZero() {
        // h = -r1 * d mod n makes nonce k1 give s = 0, so the signer must move on to k2. h and the expected signature
        // come from an independent Python signer. (r = 0 can't be forced: it needs a k whose k * G has x = 0 mod n.)
        val d = limbsOf("519b423d715f8b581f4fa8ee59f4771a5b44c8130b4e3eacca54a56dda72b464")
        val k1 = limbsOf("94a1bbb14b906a61a280f245f9e93c7f3b4a6247824f5d33b9670787642a68de")
        val k2 = limbsOf("6d3e71882c3b83b156bb14e0ab184aa9fb728068d3ae9fac421187ae0b2f34c6")
        val h = limbsOf("2a8883b9fe698ceea52dbf07cf6b2bda8f646596ac4f3e6623a050d6dda18300")

        assertNull(signWithNonce(d, h, k1.copyOf()))
        val nonces = ArrayDeque(listOf(k1.copyOf(), k2.copyOf()))
        val signature = signWithNonces(d, h) { nonces.removeFirst() }
        assertEquals(0, nonces.size, "both nonces should be drawn")
        assertEquals(
            "3045022100976d3a4e9d23326dc0baa9fa560b7c4e53f42864f508483a6473b6a11079b2db02207d1b8464bfb366c0b001db0c100ede309b6ef7e4fd85ec1228670ff5d0e0ad3e",
            signature.toHexString(),
        )
    }

    @Test
    fun testRfc6979RejectsZeroAndOutOfRangeCandidates() {
        // A fake HMAC returns queued outputs in call order. Init makes 4 calls (K, V, K, V); each next() draws V once,
        // and after a rejection first updates K and V (2 calls) before drawing again.
        val valid = ByteArray(31) + byteArrayOf(0x2a)
        val outputs = ArrayDeque(
            List(4) { ByteArray(32) { 0x11 } } + // init
                listOf(ByteArray(32) { -1 }) + // candidate >= n: rejected
                List(2) { ByteArray(32) { 0x22 } } +
                listOf(ByteArray(32)) + // candidate 0: rejected
                List(2) { ByteArray(32) { 0x33 } } +
                listOf(valid),
        )
        val nonces = Rfc6979Nonces(ByteArray(32) { 1 }, ByteArray(32) { 2 }) { _, _, _ -> outputs.removeFirst().copyOf() }
        assertContentEquals(limbsOf("2a"), nonces.next())
        assertEquals(0, outputs.size, "every queued output should be used")
    }

    @Test
    fun testReduceOnceAtAndAboveN() {
        val n = limbsOf("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")
        val nMinusOne = limbsOf("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632550")
        assertContentEquals(nMinusOne, reduceOnce(nMinusOne, n))
        assertContentEquals(IntArray(8), reduceOnce(n, n))
        assertContentEquals(limbsOf("5"), reduceOnce(limbsOf("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632556"), n))
        // 2^256 - 1 - n
        assertContentEquals(
            limbsOf("00000000ffffffff00000000000000004319055258e8617b0c46353d039cdaae"),
            reduceOnce(IntArray(8) { -1 }, n),
        )
    }

    @Test
    fun testKeyEncodings() {
        val message = "sample".encodeToByteArray()
        val padded = ByteArray(31) + byteArrayOf(0x01)
        val expected = pureEcdsaSecp256r1(padded, message)
        // Short and over-long (zero-padded) encodings of the same key
        assertContentEquals(expected, pureEcdsaSecp256r1(byteArrayOf(0x01), message))
        assertContentEquals(expected, pureEcdsaSecp256r1(ByteArray(2) + padded, message))

        assertFailsWith<IllegalArgumentException>("zero key") { pureEcdsaSecp256r1(ByteArray(32), message) }
        assertFailsWith<IllegalArgumentException>("empty key") { pureEcdsaSecp256r1(ByteArray(0), message) }
        assertFailsWith<IllegalArgumentException>("over 256 bits") { pureEcdsaSecp256r1(byteArrayOf(0x01) + padded, message) }
    }
}

class MerkleDamgardHashLengthLimitTest {
    @Test
    fun testRejectsMessagesOverTheLimit() {
        for (algorithm in listOf("md5", "sha1", "sha256")) {
            val hash = pureMerkleDamgardHashSupplier(algorithm)()
            hash.totalBytes = MAX_MESSAGE_BYTES - 10
            assertFailsWith<IllegalArgumentException>(algorithm) { hash.update(ByteArray(11)) }
            hash.update(ByteArray(10)) // exactly at the limit
            assertFailsWith<IllegalArgumentException>(algorithm) { hash.update(ByteArray(1)) }
        }
    }
}
