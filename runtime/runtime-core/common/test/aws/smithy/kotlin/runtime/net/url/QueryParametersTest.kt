/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.net.url

import kotlin.test.Test
import kotlin.test.assertEquals

class QueryParametersTest {
    @Test
    fun testParse() {
        val actual = QueryParameters.parseEncoded("?")
        val expected = QueryParameters { forceQuerySeparator = true }
        assertEquals(expected, actual)
    }
}
