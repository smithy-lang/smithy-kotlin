package aws.smithy.kotlin.runtime.util.crypto

import aws.smithy.kotlin.runtime.util.encodeToHex
import kotlin.test.Test
import kotlin.test.assertEquals

class Md5Test {
    @Test
    fun testMd5() {
        // Test a few known hashes
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5(""))
        assertEquals("0cc175b9c0f1b6a831c399e269772661", md5("a"))
        assertEquals("5c9f966da28ab24ca7796006a6259494", md5("The quick brown fox jumped over the lazy dogs."))
        assertEquals("9e7ee31a23dd8543cfd96bc56c1a9247", md5("The AWS SDK for Kotlin simplifies the use of AWS services by providing a set of libraries that are consistent and familiar for Kotlin developers."))
    }

    @Test
    fun testDigestStability() {
        val md5 = Md5()

        repeat(3) {
            assertEquals("d41d8cd98f00b204e9800998ecf8427e", md5.computeAsHex(), "Expected hash diverged on loop $it")
        }

        md5.append("a")

        repeat(3) {
            assertEquals("0cc175b9c0f1b6a831c399e269772661", md5.computeAsHex(), "Expected hash diverged on loop $it")
        }
    }

    private fun md5(input: String): String = Md5().computeAsHex(input)
}
