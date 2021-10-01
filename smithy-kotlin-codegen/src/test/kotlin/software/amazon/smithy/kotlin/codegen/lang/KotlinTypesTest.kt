/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.lang

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinTypesTest {

    @Test
    fun `it handles valid namespaces`() {
        listOf("software.amazon.smithy.kotlin.codegen.lang", "sdk").forEach {
            assertTrue(it.isValidPackageName())
        }
    }

    @Test
    fun `it fails invalid package namespaces`() {
        listOf("", "aws-packg", "some random thing", "gar@bage?", "  white space").forEach {
            assertFalse(it.isValidPackageName())
        }
    }
}
