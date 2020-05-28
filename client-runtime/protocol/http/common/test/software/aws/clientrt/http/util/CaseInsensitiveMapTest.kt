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
