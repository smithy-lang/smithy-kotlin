/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `it identifies single segment stdlib types`() {
        val testSymbol = buildSymbol {
            name = "String"
            namespace = "kotlin"
        }
        assertEquals(true, testSymbol.isBuiltIn)
    }

    @Test
    fun `it identifies multi-segment types`() {
        val testSymbol = buildSymbol {
            name = "Duration"
            namespace = "kotlin.time"
        }
        assertEquals(false, testSymbol.isBuiltIn)
    }
}
