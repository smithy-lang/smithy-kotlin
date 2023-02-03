/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinDependencyTest {
    @Test
    fun itValidatesVersions() {
        val validVersions = listOf(
            "0.1.0",
            "0.1.0-alpha",
            "1.2.3.4-beta.zulu-x",
            "0.10.0-alpha",
            "2342.138234952342.234238234-boo",
        )
        validVersions.forEach {
            assertTrue(isValidVersion(it))
        }

        val invalidVersions = listOf(
            "x.1.2",
            "0.x.2",
            "0.1.x",
            "0.1.2-fo~0",
        )
        invalidVersions.forEach {
            assertFalse(isValidVersion(it))
        }
    }
}
