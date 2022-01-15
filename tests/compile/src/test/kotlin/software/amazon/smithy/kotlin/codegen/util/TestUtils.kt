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
import software.amazon.smithy.kotlin.codegen.BuildSettings
import software.amazon.smithy.kotlin.codegen.CodegenVisitor
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.node.ObjectNode
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

private const val SmithyVersion = "1.0"

/**
 * Load and initialize a model from a Java resource URL
 */
fun String.asSmithy(sourceLocation: String = "test.smithy"): Model {
    val processed = if (!this.startsWith("\$version")) "\$version: ${SmithyVersion.doubleQuote()}\n$this" else this
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
    val compileOutput: String
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
    emitSourcesToTmp: Boolean = false
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
        compileOutputStream.toString()
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
        kotlincArguments = listOf("-Xopt-in=aws.smithy.kotlin.runtime.util.InternalApi")
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


private fun defaultSettings() = Node.objectNodeBuilder()
        .withMember(
            "package", Node.objectNode()
                .withMember("name", Node.from("test"))
                .withMember("version", Node.from("1.0.0"))
        )
        .withMember(
            "build", Node.objectNode()
                .withMember("rootProject", Node.from(true))
                .withMember("generateDefaultBuildFiles", Node.from(true))
                .withMember("multiplatform", Node.from(true))
                .withMember(
                    "optInAnnotations", Node.arrayNode(
                        Node.from("kotlin.RequiresOptIn"),
                        Node.from("aws.smithy.kotlin.runtime.util.InternalApi")
                    )
                )
        )
        .build()

// Convert KotlinSettings into JSON form for PluginContext interop
fun KotlinSettings.toObjectNode() = Node.objectNodeBuilder()
    .withMember(
        "package", Node.objectNode()
            .withMember("name", Node.from(pkg.name))
            .withMember("version", Node.from(pkg.version))
    )
    .withMember(
        "build", Node.objectNode()
            .withMember("rootProject", Node.from(build.generateFullProject))
            .withMember("generateDefaultBuildFiles", Node.from(build.generateDefaultBuildFiles))
            .withMember("multiplatform", Node.from(build.generateMultiplatformProject))
            .withMember(
                "optInAnnotations",
                Node.arrayNode(*(build.optInAnnotations?.map { Node.from(it) } ?: emptyList<Node>()).toTypedArray())
            )
    )
    .build()


// Produce the generated service code given model inputs.
fun generateSdk(
    model: Model,
    manifest: MockManifest = MockManifest(),
    settings: ObjectNode = defaultSettings(),
    integrationCurator: (List<KotlinIntegration>) -> List<KotlinIntegration> = { it }
): MockManifest {
    // Initialize context
    val pluginContext = PluginContext
        .builder()
        .model(model)
        .fileManifest(manifest)
        .settings(settings)
        .build()

    // Generate SDK
    CodegenVisitor(pluginContext, integrationCurator).execute()

    return manifest
}

// Find the root directory of the source project
fun findProjectRoot(projectDirectoryName: String = "smithy-kotlin"): String {
    val currentPath = System.getProperty("user.dir")
        .split(File.separator)
        .filterNot { it.isEmpty() }
        .toMutableList()

    var projectDirFound = false
    val projectRootPath = StringBuilder()
    while(!projectDirFound && currentPath.isNotEmpty()) {
        val ns = currentPath.removeFirst()
        if (ns == projectDirectoryName) projectDirFound = true
        projectRootPath.append(File.separator)
        projectRootPath.append(ns)
    }

    if (!projectDirFound) error("Unexpectedly unable to find project root")
    if (projectRootPath.isEmpty()) error("Unable to determine project root path")

    return projectRootPath.toString()
}