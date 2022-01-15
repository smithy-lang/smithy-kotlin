package software.amazon.smithy.kotlin.codegen

import aws.smithy.kotlin.runtime.util.OsFamily
import aws.smithy.kotlin.runtime.util.Platform
import com.tschuchort.compiletesting.KotlinCompilation
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.lang.hardReservedWords
import software.amazon.smithy.kotlin.codegen.util.CodegenTestIntegration
import software.amazon.smithy.kotlin.codegen.util.asSmithy
import software.amazon.smithy.kotlin.codegen.util.compileSdkAndTest
import software.amazon.smithy.kotlin.codegen.util.findProjectRoot
import software.amazon.smithy.kotlin.codegen.util.generateSdk
import software.amazon.smithy.kotlin.codegen.util.toObjectNode
import software.amazon.smithy.kotlin.codegen.util.writeToDirectory
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ShapeId
import java.io.ByteArrayOutputStream
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests that validate the generated source for a white-label SDK
 */
class SmithySdkTest {
    // Max number of warnings the compiler can issue as a result of compiling SDK with kitchen sink model.
    private val warningThreshold = 3

    @Test
    fun `smithy sdk compiles without errors`() {
        val model = javaClass.getResource("/kitchen-sink-model.smithy")!!.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(
            model = model,
            outputSink = compileOutputStream,
            emitSourcesToTmp = Debug.emitSourcesToTemp
        )
        compileOutputStream.flush()

        assertEquals(compilationResult.exitCode, KotlinCompilation.ExitCode.OK, compileOutputStream.toString())
    }

    @Test
    fun `smithy sdk compiles with language keywords as model member names`() {
        val fooMembers = hardReservedWords
            .filterNot { it.indexOf('?') >= 0 || it.indexOf('!') >= 0 }
            .joinToString(separator = ",\n") { keyword -> "$keyword: String" }

        val model = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    Foo,
                ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                input: FooStruct,
                output: FooStruct,
                errors: [FooErrorStruct]
            }
                       
            structure FooStruct {
                $fooMembers
            }
            
            @error("client")
            structure FooErrorStruct {
                $fooMembers
            }
        """.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(
            model = model,
            outputSink = compileOutputStream,
            emitSourcesToTmp = Debug.emitSourcesToTemp
        )
        compileOutputStream.flush()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode, compileOutputStream.toString())
    }

    @Test
    fun `it has non conflicting document deserializer for exceptions`() {
        // test that an exception is re-used not as an error but as part of some other payload (ticket: 176989575)
        val model = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [ Foo ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                input: FooRequest,
                output: FooOutput,
                errors: [FooError]
            }        
            
            structure FooRequest {}
            structure FooOutput {
                err: FooError
            }
            
            @error("server")
            structure FooError { 
            }
        """.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(
            model = model,
            outputSink = compileOutputStream,
            emitSourcesToTmp = Debug.emitSourcesToTemp
        )
        compileOutputStream.flush()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode, compileOutputStream.toString())
    }

    @Test
    fun `it compiles models with nested unions`() {
        val model = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    Foo,
                ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation Foo {
                output: FooResponse
            }

            structure FooResponse { 
                unionMember1: Union1
            }
            
            union Union1 {
                unionMember2: Union2
            }
            
            union Union2 {
                fooMember: String
            }
        """.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(
            model = model,
            outputSink = compileOutputStream,
            emitSourcesToTmp = Debug.emitSourcesToTemp
        )
        compileOutputStream.flush()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode, compileOutputStream.toString())
    }

    // The difference between this and the compile test above is that
    // this test validates the entire SDK (eg build files) whereas the previous
    // test validates only the Kotlin source against the existing classpath.
    @Test
    fun `smithy sdk builds without errors`() {
        val model = javaClass.getResource("/kitchen-sink-model.smithy")!!.asSmithy()

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

    // This function takes a manifest of a generated SDK, saves it to a temp directory, and invokes the
    // project's gradle program to build it.
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

/**
 * Load and initialize a model from a Java resource URL
 */
fun URL.asSmithy(): Model =
    Model.assembler()
        .addImport(this)
        .discoverModels()
        .assemble()
        .unwrap()
