package aws.smithy.kotlin.runtime.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CaseInsensitiveStringTest {
    @Test
    fun testEquality() {
        val left = "Banana".toInsensitive()
        val right = "baNAna".toInsensitive()
        assertEquals(left, right)
        assertNotEquals<Any>("Banana", left)
        assertNotEquals<Any>("baNAna", right)

        val nonMatching = "apple".toInsensitive()
        assertNotEquals(nonMatching, left)
        assertNotEquals(nonMatching, right)
    }

    @Test
    fun testProperties() {
        val s = "BANANA".toInsensitive()
        assertEquals("BANANA", s.original)
        assertEquals("banana", s.normalized)
    }
}
