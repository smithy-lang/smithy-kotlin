/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.dokka

import org.jsoup.Jsoup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DokkaSmithyTest {
    @Test
    fun testLoadScripts() {
        val testFile = File(outputDir("html"), "index.html")

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

    @Test
    fun testMarkdownPublication() {
        val testFile = File(outputDir("markdown"), "dokka-smithy/aws.smithy.kotlin.dokka/-smithy-dokka-plugin/index.md")

        assertTrue(
            testFile.exists(),
            "Test file does not exist: ${testFile.absolutePath}",
        )

        assertTrue(
            testFile.readLines().any { it == "# SmithyDokkaPlugin" },
            "Expected heading for SmithyDokkaPlugin not found",
        )
    }
}

private fun outputDir(format: String): File {
    val path = System.getProperty("dokka.$format.outputDir")
    assertNotNull(path, "System property dokka.$format.outputDir is not set")
    return File(path)
}
