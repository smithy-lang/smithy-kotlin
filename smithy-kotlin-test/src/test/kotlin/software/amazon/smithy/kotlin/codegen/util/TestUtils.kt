/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
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
import java.io.File
import java.net.URL

/**
 * Load and initialize a model from a Java resource URL
 */
fun URL.asSmithy(): Model =
    Model.assembler()
        .addImport(this)
        .discoverModels()
        .assemble()
        .unwrap()

private const val SmithyVersion = "1.0"
/**
 * Load and initialize a model from a Java resource URL
 */
fun String.asSmithy(sourceLocation: String? = null): Model {
    val processed = letIf(!this.startsWith("\$version")) { "\$version: ${SmithyVersion.doubleQuote()}\n$it" }
    return Model.assembler().discoverModels().addUnparsedModel(sourceLocation ?: "test.smithy", processed).assemble().unwrap()
}

private fun String.doubleQuote(): String = "\"${this.slashEscape('\\').slashEscape('"')}\""
private fun String.slashEscape(char: Char) = this.replace(char.toString(), """\$char""")
private fun <T> T.letIf(cond: Boolean, f: (T) -> T): T {
    return if (cond) {
        f(this)
    } else this
}

data class ModelChangeTestResult(
        val originalModelCompilationResult: KotlinCompilation.Result,
        val updatedModelCompilationResult: KotlinCompilation.Result,
        val compileSuccess: Boolean
)

fun testModelChangeAgainstSource(originalModel: Model, updatedModel: Model, testSource: String, emitSourcesToTmp: Boolean = false): ModelChangeTestResult {
    val originalModelCompilationResult = compileSdkAndTest(originalModel, testSource, emitSourcesToTmp)
    val updatedModelCompilationResult = compileSdkAndTest(updatedModel, testSource, emitSourcesToTmp)

    return ModelChangeTestResult(
            originalModelCompilationResult,
            updatedModelCompilationResult,
            originalModelCompilationResult.exitCode == KotlinCompilation.ExitCode.OK &&
                    updatedModelCompilationResult.exitCode == KotlinCompilation.ExitCode.OK
    )
}

fun compileSdkAndTest(model: Model, testSource: String, emitSourcesToTmp: Boolean = false): KotlinCompilation.Result {
    val testSourceFile = SourceFile.kotlin("test.kt", testSource)
    val sdkFileManifest = generateSdk(model)

    if (emitSourcesToTmp) {
        val buildRootDir = "/tmp/sdk-codegen-${System.currentTimeMillis()}"
        check(!File(buildRootDir).exists()) { "SDK output directory $buildRootDir already exists, aborting." }
        sdkFileManifest.writeToDirectory(buildRootDir)
        println("Wrote generated SDK to $buildRootDir")
    }

    val sdkSources = sdkFileManifest.toSourceFileList() + testSourceFile

    // Run test against
    return KotlinCompilation().apply {
        sources = sdkSources
        inheritClassPath = true
        messageOutputStream = System.out
    }.compile()
}

// generateSdk(model2).writeToDirectory("/tmp/kt2")
fun MockManifest.writeToDirectory(dir: String) {
    files
            .map { path -> File(dir, path.toString()) to expectFileString(path) }
            .forEach { (file, content) ->
                if (!file.parentFile.exists()) check(file.parentFile.mkdirs()) { "Unable to create directory ${file.parentFile}"}
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
                .withMember("module", Node.from("test"))
                .withMember("moduleVersion", Node.from("1.0.0"))
                .build()
): MockManifest {
    // Initialize context
    val pluginContext = PluginContext
            .builder()
            .model(model)
            .fileManifest(manifest)
            .settings(settings)
            .build()

    // Generate SDK
    CodegenVisitor(pluginContext).also { it.execute() }

    return manifest
}