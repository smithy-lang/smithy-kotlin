/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import software.amazon.smithy.kotlin.codegen.lang.hardReservedWords
import software.amazon.smithy.kotlin.codegen.util.asSmithy
import software.amazon.smithy.kotlin.codegen.util.compileSdkAndTest
import software.amazon.smithy.model.Model
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests that validate the generated source for a white-label SDK
 */
@OptIn(ExperimentalCompilerApi::class)
class SmithySdkTest {
    // Max number of warnings the compiler can issue as a result of compiling SDK with kitchen sink model.
    private val warningThreshold = 3

    @Test
    fun `white label sdk compiles without errors`() {
        val model = javaClass.getResource("/kitchen-sink-model.smithy")!!.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = Debug.emitSourcesToTemp)
        compileOutputStream.flush()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode, compileOutputStream.toString())
    }

    // FIXME - disabled until we invest time into improving the extraneous warnings we get for things like parameter never used, etc
    @Test
    @Ignore
    fun `white label sdk compiles without breaching warning threshold`() {
        val model = javaClass.getResource("/kitchen-sink-model.smithy")!!.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = Debug.emitSourcesToTemp)
        compileOutputStream.flush()

        val warnings = compilationResult.messages
            .split("\n")
            .filter { it.startsWith("w: ") }

        val result = warnings.count()
        val formatted = warnings.joinToString(separator = "\n")
        assertTrue(result <= warningThreshold, "Compiler warnings ($result) breached threshold of $warningThreshold\n$formatted")
    }

    @Test
    fun `white label sdk compiles with language keywords as model member names`() {
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
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = Debug.emitSourcesToTemp)
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
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = Debug.emitSourcesToTemp)
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
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = Debug.emitSourcesToTemp)
        compileOutputStream.flush()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode, compileOutputStream.toString())
    }

    // https://github.com/smithy-lang/smithy-kotlin/issues/1125
    @Test
    fun `it compiles models with string enums`() {
        val model = """
            namespace com.test

            use aws.protocols#restXml

            @restXml
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
                payload: StringBasedEnumList
            }
            
            @enum([
                {
                    value: "blarg",
                    name: "Blarg"
                },
                {
                    value: "blergh",
                    name: "Blergh"
                }
            ])
            string StringBasedEnum
            
            list StringBasedEnumList {
                member: StringBasedEnum
            }
        """.asSmithy()

        val compileOutputStream = ByteArrayOutputStream()
        val compilationResult = compileSdkAndTest(model = model, outputSink = compileOutputStream, emitSourcesToTmp = Debug.emitSourcesToTemp)
        compileOutputStream.flush()

        assertEquals(KotlinCompilation.ExitCode.OK, compilationResult.exitCode, compileOutputStream.toString())
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
