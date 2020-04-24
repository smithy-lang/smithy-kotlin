package com.amazonaws.clientrt

import com.amazonaws.clientrt.smithy.*

import kotlin.test.*

class DocumentBuilderTest {
    @Test
    fun `builds an object`() {
        val doc = document {
            "foo" to 1
            "baz" to documentArray {
                +n(202L)
                +n(12)
                +true
                +"blah"
            }
        }

        val expected = """{"foo":1,"baz":[202,12,true,"blah"]}"""

        assertEquals(expected, "$doc")
    }
}
