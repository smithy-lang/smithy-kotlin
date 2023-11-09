/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.auth

import aws.smithy.kotlin.runtime.auth.AuthOption
import aws.smithy.kotlin.runtime.auth.AuthSchemeId
import aws.smithy.kotlin.runtime.util.attributesOf
import aws.smithy.kotlin.runtime.util.get
import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointAuthTest {
    @Test
    fun testMerge() {
        val modeledOptions = listOf(
            AuthOption(AuthSchemeId.AwsSigV4, attributesOf { "k1" to "v1"; "k4" to "v4" }),
        )

        val endpointOptions = listOf(
            AuthOption(AuthSchemeId.AwsSigV4Asymmetric),
            AuthOption(AuthSchemeId.AwsSigV4, attributesOf { "k1" to "v2"; "k2" to "v3" }),
        )

        val actual = mergeAuthOptions(modeledOptions, endpointOptions)
        val expected = listOf(
            AuthOption(AuthSchemeId.AwsSigV4Asymmetric),
            AuthOption(AuthSchemeId.AwsSigV4, attributesOf { "k1" to "v2"; "k2" to "v3"; "k4" to "v4" }),
        )

        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { idx, expectedOption ->
            val actualOption = actual[idx]
            assertEquals(expectedOption.schemeId, actualOption.schemeId)
            assertEquals(expectedOption.attributes, actualOption.attributes)
        }
    }
}
