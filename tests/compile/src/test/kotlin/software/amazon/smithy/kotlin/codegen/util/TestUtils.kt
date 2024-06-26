/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.util

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.kotlin.codegen.CodegenVisitor
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

private const val SMITHY_VERSION = "2.0"

/**
 * Load and initialize a model from a Java resource URL
 */
fun String.asSmithy(sourceLocation: String = "test.smithy"): Model {
    val processed = if (!this.startsWith("\$version")) "\$version: ${SMITHY_VERSION.doubleQuote()}\n$this" else this
    return Model.assembler().discoverModels().addUnparsedModel(sourceLocation, processed).assemble().unwrap()
}

private fun String.doubleQuote(): String = "\"${this.slashEscape('\\').slashEscape('"')}\""
private fun String.slashEscape(char: Char) = this.replace(char.toString(), """\$char""")

/**
 * Captures the result of a model transformation test
 */
data class ModelChangeTestResult(
    val originalModelCompilationResult: KotlinCompilation.Result,
    val updatedModelCompilationResult: KotlinCompilation.Result,
    val compileSuccess: Boolean,
    val compileOutput: String,
)

/**
 * Generate an SDK based on [originalModel], compile with [testSource], then do the same with the [updatedModel].  Return the
 * results of both compilation cycles.
 *
 * @param originalModel source model to generate an SDK from and to test against [testSource]
 * @param updatedModel model updates which to test against [testSource]
 * @param testSource Kotlin code intended to compile against both [originalModel] and [updatedModel]
 * @param emitSourcesToTmp a debugging function to emit generated SDKs to a temp directory for analysis.  Actual
 * target directory is provided in log output.
 */
fun testModelChangeAgainstSource(
    originalModel: Model,
    updatedModel: Model,
    testSource: String,
    emitSourcesToTmp: Boolean = false,
): ModelChangeTestResult {
    val compileOutputStream = ByteArrayOutputStream()
    val originalModelCompilationResult =
        compileSdkAndTest(originalModel, testSource, compileOutputStream, emitSourcesToTmp)
    val updatedModelCompilationResult =
        compileSdkAndTest(updatedModel, testSource, compileOutputStream, emitSourcesToTmp)
    compileOutputStream.flush()

    return ModelChangeTestResult(
        originalModelCompilationResult,
        updatedModelCompilationResult,
        originalModelCompilationResult.exitCode == KotlinCompilation.ExitCode.OK &&
            updatedModelCompilationResult.exitCode == KotlinCompilation.ExitCode.OK,
        compileOutputStream.toString(),
    )
}

/**
 * Generate an SDK based on input model, then compile it and the [testSource] together, and return the result of compilation.
 *
 * @param model input model from which the SDK is generated
 * @param testSource optional Kotlin source code to be compiled with SDK
 * @param outputSink where to emit compiler messages to, defaults to stdout.
 * @param emitSourcesToTmp a debugging function to emit generated SDK to a temp directory for analysis.  Actual
 * target directory is provided in log output.
 */
fun compileSdkAndTest(
    model: Model,
    testSource: String? = null,
    outputSink: OutputStream = System.out,
    emitSourcesToTmp: Boolean = false,
): KotlinCompilation.Result {
    val sdkFileManifest = generateSdk(model)

    if (emitSourcesToTmp) {
        val buildRootDir = "/tmp/sdk-codegen-${System.currentTimeMillis()}"
        check(!File(buildRootDir).exists()) { "SDK output directory $buildRootDir already exists, aborting." }
        sdkFileManifest.writeToDirectory(buildRootDir)
        println("Wrote generated SDK to $buildRootDir")
    }

    val sdkSources = if (testSource != null) {
        val testSourceFile = SourceFile.kotlin("test.kt", testSource)
        sdkFileManifest.toSourceFileList() + testSourceFile
    } else {
        sdkFileManifest.toSourceFileList()
    }

    // Run test against
    return KotlinCompilation().apply {
        kotlincArguments = listOf(
            "-Xopt-in=aws.smithy.kotlin.runtime.InternalApi",
            "-Xexplicit-api=strict",
        )
        sources = sdkSources
        inheritClassPath = true
        messageOutputStream = outputSink
        jvmTarget = "1.8"
    }.compile()
}

// Ex: generateSdk(model2).writeToDirectory("/tmp/mysdk")
fun MockManifest.writeToDirectory(dir: String) {
    files
        .map { path -> File(dir, path.toString()) to expectFileString(path) }
        .forEach { (file, content) ->
            if (!file.parentFile.exists()) check(file.parentFile.mkdirs()) { "Unable to create directory ${file.parentFile}" }
            file.writeText(content)
        }
}

// Convert a MockManifest into the Source File list expected by the compiler tool.
fun MockManifest.toSourceFileList() =
    files
        .filter { file -> file.toString().endsWith(".kt") }
        .map { file -> SourceFile.kotlin(file.fileName.toString(), expectFileString(file)) }

// Produce the generated service code given model inputs.
fun generateSdk(
    model: Model,
    manifest: MockManifest = MockManifest(),
    settings: ObjectNode = Node.objectNodeBuilder()
        .withMember(
            "package",
            Node.objectNode()
                .withMember("name", Node.from("test"))
                .withMember("version", Node.from("1.0.0")),
        )
        .build(),
): MockManifest {
    // Initialize context
    val pluginContext = PluginContext
        .builder()
        .model(model)
        .fileManifest(manifest)
        .settings(settings)
        .build()

    // Generate SDK
    CodegenVisitor(pluginContext).execute()

    return manifest
}
