package software.amazon.smithy.kotlin.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.util.asSmithy
import software.amazon.smithy.kotlin.codegen.util.compileSdkAndTest
import software.amazon.smithy.kotlin.codegen.util.testModelChangeAgainstSource
import software.amazon.smithy.model.Model
import java.io.ByteArrayOutputStream
import java.net.URL

/**
 * Tests that validate the generated source for a white-label SDK
 */
class WhiteLabelSDKTest {
    // Max number of warnings the compiler can issue as a result of compiling SDK.
    private val warningThreshold = 3
    private val copyGeneratedSdksToTmp = true

    @Test
    fun `white label sdk compiles without errors`() {
        val model = javaClass.getResource("/kitchen-sink-model.smithy").asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = copyGeneratedSdksToTmp)
        compileOutputStream.flush()

        assertTrue(compilationResult.exitCode == KotlinCompilation.ExitCode.OK, compileOutputStream.toString())

        println(compilationResult.messages)
    }

    @Test
    fun `white label sdk compiles without breaching warning threshold`() {
        val model = javaClass.getResource("/kitchen-sink-model.smithy").asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = copyGeneratedSdksToTmp)
        compileOutputStream.flush()

        val result = compilationResult.messages
            .split("\n")
            .filter { it.startsWith("w: ") }
            .count()

        assertTrue( result <= warningThreshold, "Compiler warnings ($result) breached threshold of $warningThreshold.")
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