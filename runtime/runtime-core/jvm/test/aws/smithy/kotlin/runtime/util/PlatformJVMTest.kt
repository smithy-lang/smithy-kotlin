/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.writeText
import kotlin.test.*

class PlatformJVMTest {

    private val fileContent = "This is the file content.  ⥳⏗⣱⧲⅏⻃Ⱊ⅘"
    private lateinit var tempFile: Path

    @BeforeTest
    fun writeTempFile() {
        tempFile = Files.createTempFile("prefix", "postfix")
        tempFile.writeText(fileContent)
    }

    @Test
    fun itReadsFiles() = runTest {
        val actual = PlatformProvider.System.readFileOrNull(tempFile.absolutePathString())

        assertNotNull(actual)
        assertEquals(fileContent, actual.decodeToString())
    }

    @Test
    fun testGetAllEnvVars() {
        val allEnvVarsFromSystem = System.getenv()
        val allEnvVarsFromPlatform = PlatformProvider.System.getAllEnvVars()
        assertEquals(allEnvVarsFromSystem, allEnvVarsFromPlatform)
    }

    @Test
    fun testGetAllProperties() {
        val allPropertiesFromSystem = System
            .getProperties()
            .entries
            .associate { (key, value) -> key.toString() to value.toString() }
        val allPropertiesFromPlatform = PlatformProvider.System.getAllProperties()
        assertEquals(allPropertiesFromSystem, allPropertiesFromPlatform)
    }

    @Test
    fun testFileExists() = runTest {
        assertTrue(PlatformProvider.System.fileExists(tempFile.absolutePathString()))
    }
}
