/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.core

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.TestModelDefault
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff

class KotlinWriterTest {

    @Test
    fun `writes doc strings`() {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        writer.dokka("These are the docs.\nMore.")
        val result = writer.toString()
        Assertions.assertTrue(result.contains("/**\n * These are the docs.\n * More.\n */\n"))
    }

    @Test
    fun `escapes $ in doc strings`() {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val docs = "This is $ valid documentation."
        writer.dokka(docs)
        val result = writer.toString()
        Assertions.assertTrue(result.contains("/**\n * " + docs + "\n */\n"))
    }

    /**
     * This is \*\/ valid documentation.
     */
    @Test
    fun `escapes comment tokens in doc strings`() {
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val docs = "This is */ valid /* documentation."
        writer.dokka(docs)
        val actual = writer.toString()
        val expected = "This is *&#47; valid &#47;* documentation."
        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun `it strips html tags from doc strings`() {
        val unit = KotlinWriter(TestModelDefault.NAMESPACE)
        val docs = "<p>here is <b>some</b> sweet <i>sweet</i> <a>html</a></p>"
        unit.dokka(docs)
        val actual = unit.toString()
        actual.shouldContainOnlyOnceWithDiff("here is some sweet sweet html")
    }
}
