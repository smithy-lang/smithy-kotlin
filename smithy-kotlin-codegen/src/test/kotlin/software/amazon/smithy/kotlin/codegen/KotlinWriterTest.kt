/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class KotlinWriterTest {

    @Test fun `writes doc strings`() {
        val writer = KotlinWriter("com.test")
        writer.dokka("These are the docs.\nMore.")
        val result = writer.toString()
        Assertions.assertTrue(result.contains("/**\n * These are the docs.\n * More.\n */\n"))
    }

    @Test fun `escapes $ in doc strings`() {
        val writer = KotlinWriter("com.test")
        val docs = "This is $ valid documentation."
        writer.dokka(docs)
        val result = writer.toString()
        Assertions.assertTrue(result.contains("/**\n * " + docs + "\n */\n"))
    }
}
