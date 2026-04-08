/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.dokka

import org.jsoup.Jsoup
import aws.smithy.kotlin.runtime.testing.TestInstance
import aws.smithy.kotlin.runtime.testing.TestLifecycle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

@TestInstance(TestLifecycle.PER_CLASS)
class DokkaSmithyTest {
    @Test
    fun testLoadScripts() {
        val testFile = File("build/dokka/html/index.html")

        assertTrue(
            testFile.exists(),
            "Test file does not exist: ${testFile.absolutePath}",
        )

        val document = Jsoup.parse(testFile, "UTF-8")

        val expectedScripts = listOf(
            "awshome_s_code.js",
        )

        val scripts = document.head().select("script[src]")

        expectedScripts.forEach { expectedScript ->
            assertTrue(
                scripts.any { it.attr("src").endsWith(expectedScript) },
                "Expected script $expectedScript not found",
            )
        }
    }
}
