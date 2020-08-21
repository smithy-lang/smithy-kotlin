/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.aws.clientrt.http.util

import io.kotest.matchers.string.shouldContain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class HeaderListsTest {
    @Test
    fun `it splits on comma`() {
        assertEquals(listOf("1"), splitHeaderListValues("1"))
        assertEquals(listOf("1", "2", "3"), splitHeaderListValues("1,2,3"))
        assertEquals(listOf("1", "2", "3"), splitHeaderListValues("1,  2,  3"))
        assertEquals(listOf("", "1"), splitHeaderListValues(",1"))
    }

    @Test
    fun `it splits httpDate lists`() {
        // input to expected
        val tests = listOf(
            // no split
            "Mon, 16 Dec 2019 23:48:18 GMT" to listOf("Mon, 16 Dec 2019 23:48:18 GMT"),
            // with split
            "Mon, 16 Dec 2019 23:48:18 GMT, Tue, 17 Dec 2019 23:48:18 GMT" to listOf(
                "Mon, 16 Dec 2019 23:48:18 GMT",
                "Tue, 17 Dec 2019 23:48:18 GMT"
            ),
            // empty
            "" to listOf("")
        )

        for (test in tests) {
            assertEquals(test.second, splitHttpDateHeaderListValues(test.first))
        }

        val ex = assertFails {
            splitHttpDateHeaderListValues("Mon, 16 Dec 2019 23:48:18 GMT, , Tue, 17 Dec 2019 23:48:18 GMT")
        }
        ex.message!!.shouldContain("invalid timestamp HttpDate header comma separations: `Mon")
    }
}
