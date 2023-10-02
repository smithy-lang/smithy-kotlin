/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package smithy.kotlin.nullability

import kotlin.test.Test
import kotlin.test.assertNull

class DefaultValueTest {
    @Test
    fun testDefaultsClientMode() {
        val actual = smithy.kotlin.nullability.client.model.SayHelloRequest.Builder()
            .build()

        // all members of structure marked with `@input` are implicitly `@clientOptional`
        assertNull(actual.tay)
    }

    @Test
    fun testDefaultsClientCarefulMode() {
        val actual = smithy.kotlin.nullability.clientcareful.model.SayHelloRequest.Builder()
            .build()

        // all members of structure marked with `@input` are implicitly `@clientOptional`
        assertNull(actual.tay)
    }
}
