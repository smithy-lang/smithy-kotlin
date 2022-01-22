/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import aws.smithy.kotlin.runtime.util.OsFamily
import aws.smithy.kotlin.runtime.util.Platform
import kotlin.test.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.util.CodegenTestIntegration
import software.amazon.smithy.kotlin.codegen.util.findProjectRoot
import software.amazon.smithy.kotlin.codegen.util.generateSdk
import software.amazon.smithy.kotlin.codegen.util.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.util.toObjectNode
import software.amazon.smithy.kotlin.codegen.util.writeToDirectory
import software.amazon.smithy.model.shapes.ShapeId
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.test.assertTrue

/**
 * Tests that evaluate the entire output of a codegen run as a compilable and executable unit
 *
 * The difference between this and the compile tests in SmithySdkTest.kt is that
 * this test validates the entire SDK (eg build files) whereas the other
 * test validates only the Kotlin source against the existing classpath.
 */
class StandAloneProjectTest {

    @Test
    fun `all variations of build settings produce buildable projects`() {

        val model = loadModelFromResource("kitchen-sink-model.smithy")

        val baseSettings = KotlinSettings(
            ShapeId.from("com.test#Example"),
            KotlinSettings.PackageSettings("test", "1.0.0"),
            "sdkId",
            BuildSettings(
                generateFullProject = true,
                generateDefaultBuildFiles = true,
                optInAnnotations = listOf("kotlin.RequiresOptIn", "aws.smithy.kotlin.runtime.util.InternalApi"),
                generateMultiplatformProject = true // Test that KMP project compiles
            )
        )

        val settingsToTest = listOf(
            baseSettings,
            baseSettings.copy(build = baseSettings.build.copy(generateMultiplatformProject = false))
        )
        val enableProtocolGenerator = listOf(true, false)

        enableProtocolGenerator.forEach { enabled ->
            settingsToTest.forEach { setting ->
                val manifest = generateSdk(
                    model = model,
                    settings = setting.toObjectNode()
                ) { integrationList ->
                    if (enabled) {
                        integrationList
                    } else {
                        integrationList.toMutableList().filterNot {
                            // Remove the test protocol so a default client is not generated
                            it is CodegenTestIntegration
                        }
                    }
                }

                saveAndBuildSdk(manifest)
            }
        }
    }

    /**
     * This function takes a manifest of a generated SDK, saves it to a temp directory, and invokes the
     * host project's gradle program to build it.
     * @param manifest Manifest of codegen files to write
     * @param gradleTask Task in target project to execute
     */
    private fun saveAndBuildSdk(manifest: MockManifest, gradleTask: String = "build") {
        val sdkBuildDir = Files.createTempDirectory("smithy-sdk")
        println("writing to $sdkBuildDir")
        manifest.writeToDirectory(sdkBuildDir.absolutePathString())

        // Find the root of the source project
        val projectRootPath = findProjectRoot()

        // Determine the gradle command to run based on local system OS
        val gradleCmd = if (Platform.osInfo().family == OsFamily.Windows) "gradlew.bat" else "gradlew"

        // Execute the project's gradle to build the codegened SDK
        println("Running '$projectRootPath/$gradleCmd $gradleTask' in directory $sdkBuildDir")
        val process = ProcessBuilder(listOf("$projectRootPath/$gradleCmd", gradleTask))
            .directory(sdkBuildDir.toFile())
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(ProcessBuilder.Redirect.PIPE)
            .start()

        val completed = process.waitFor(2, TimeUnit.MINUTES)
        val stdOut = process.inputStream.reader().readText()
        val stdError = process.errorStream.reader().readText()

        assertTrue(completed, "Timed out while waiting for external project compilation to complete")
        assertTrue(
            process.exitValue() == 0,
            """
                Build process returned non-zero exit value '${process.exitValue()}' 
                from source in ${sdkBuildDir.absolutePathString()}
                
                stdout:
                $stdOut
                
                stderr:
                $stdError
            """.trimIndent()
        )
    }
}