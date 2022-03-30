package aws.smithy.kotlin.runtime.util.crypto

import aws.smithy.kotlin.runtime.util.encodeToHex
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import kotlin.test.assertEquals

class Md5JvmComparisonTest {
    @Test
    fun testMd5() {
        val buffer = StringBuilder()
        (0..1023).forEach {
            buffer.append(it.toChar())
            val payload = buffer.toString()
            val expected = md5Jvm(payload)
            val actual = md5(payload)
            assertEquals(expected, actual, "Expected MD5 sum for $payload to be $expected but was $actual")
        }
    }

    private fun md5(input: String): String = Md5().computeAsHex(input)
    private fun md5Jvm(input: String): String {
        val md5 = MessageDigest.getInstance("MD5");
        md5.update(input.encodeToByteArray())
        val digest = md5.digest()
        return digest.encodeToHex()
    }
}