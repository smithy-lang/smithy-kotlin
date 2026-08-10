/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.schema

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShapeTypeTest {
    private val simpleTypes = setOf(
        ShapeType.BLOB, ShapeType.BOOLEAN, ShapeType.STRING, ShapeType.TIMESTAMP,
        ShapeType.BYTE, ShapeType.SHORT, ShapeType.INTEGER, ShapeType.LONG,
        ShapeType.FLOAT, ShapeType.DOUBLE, ShapeType.BIG_INTEGER, ShapeType.BIG_DECIMAL,
        ShapeType.DOCUMENT, ShapeType.ENUM, ShapeType.INT_ENUM,
    )

    @Test
    fun testSimpleTypesAreSimple() {
        for (t in simpleTypes) assertTrue(t.isSimple, "$t should be simple")
    }

    @Test
    fun testNonSimpleTypesAreNotSimple() {
        for (t in ShapeType.entries) {
            if (t !in simpleTypes) assertFalse(t.isSimple, "$t should not be simple")
        }
    }
}
