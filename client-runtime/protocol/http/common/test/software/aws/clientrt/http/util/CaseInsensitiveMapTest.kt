/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.http.util

import kotlin.test.Test
import kotlin.test.assertEquals

class CaseInsensitiveMapTest {
    @Test
    fun `map operations ignore case`() {
        val map = CaseInsensitiveMap<String>()

        map["conTent-Type"] = "json"
        assertEquals("json", map["conTent-Type"])
        assertEquals("json", map["Content-Type"])
        assertEquals("json", map["content-type"])
        assertEquals("json", map["CONTENT-TYPE"])
    }
}
