/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.samples

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.kotlin.codegen.model.hasAllOptionalMembers
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.SourceLocation
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.traits.ExamplesTrait
import software.amazon.smithy.model.traits.ExamplesTrait.Example
import software.amazon.smithy.model.transform.ModelTransformer
import java.util.*
import kotlin.jvm.optionals.getOrDefault

/**
 * [KotlinIntegration] that renders [KDoc samples](https://kotlinlang.org/docs/kotlin-doc.html#sample-identifier)
 * and pre-processes the documentation to insert references to the generated sample identifiers for operations
 * that have [examples](https://smithy.io/2.0/spec/documentation-traits.html#smithy-api-examples-trait)
 */
class KDocSamplesGenerator : KotlinIntegration {
    override fun enabledForService(model: Model, settings: KotlinSettings): Boolean {
        val topDownIndex = TopDownIndex.of(model)
        val operations = topDownIndex.getContainedOperations(settings.service)
        return operations.any { it.hasTrait<ExamplesTrait>() }
    }

    /**
     * This should run _after_ [software.amazon.smithy.kotlin.codegen.lang.DocumentationPreprocessor]
     */
    override fun preprocessModel(model: Model, settings: KotlinSettings): Model {
        val transformer = ModelTransformer.create()
        return transformer.mapShapes(model) { shape ->
            when {
                shape is OperationShape && shape.hasTrait<ExamplesTrait>() -> {
                    val examplesTrait = shape.expectTrait<ExamplesTrait>()
                    val filtered = examplesTrait.examples.filterNot { it.error.isPresent }
                    val kdocSampleIdentifiers = filtered.indices.joinToString(separator = "\n") { idx ->
                        val identifier = sampleIdentifier(settings, shape, idx)
                        "@sample $identifier"
                    }

                    val existingDocs = shape.getTrait<DocumentationTrait>()
                    val updatedDocs = buildString {
                        if (existingDocs != null) {
                            append(existingDocs.value)
                            append("\n\n")
                        }
                        append(kdocSampleIdentifiers)
                    }
                    val sourceLocation = existingDocs?.sourceLocation ?: SourceLocation.NONE
                    val newOrUpdatedDocTrait = DocumentationTrait(updatedDocs, sourceLocation)
                    shape.toBuilder()
                        .addTrait(newOrUpdatedDocTrait)
                        .build()
                }
                else -> shape
            }
        }
    }

    private fun sampleIdentifier(settings: KotlinSettings, op: OperationShape, index: Int): String =
        listOf(
            samplePackage(settings),
            sampleClassName(op),
            sampleFunctionName(index),
        ).joinToString(separator = ".")

    private fun sampleFunctionName(index: Int): String = "sample" + if (index > 0) "$index" else ""
    private fun sampleClassName(op: OperationShape): String = op.id.name

    private fun samplePackage(settings: KotlinSettings): String = settings.pkg.subpackage("samples")

    override fun writeAdditionalFiles(ctx: CodegenContext, delegator: KotlinDelegator) {
        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(ctx.settings.service)
        operations.filter { it.hasTrait<ExamplesTrait>() }
            .forEach { op ->
                val examples = op.expectTrait<ExamplesTrait>()

                val writer = KotlinWriter(samplePackage(ctx.settings))
                writer.withBlock("class #L {", "}", sampleClassName(op)) {
                    examples
                        .examples
                        // unclear what benefit error examples provide, omit for now
                        .filterNot { it.error.isPresent }
                        .forEachIndexed { idx, example ->
                            write("")
                            write("@Sample")
                            withBlock("fun #L() {", "}", sampleFunctionName(idx)) {
                                example
                                    .documentation
                                    .getOrDefault(example.title)
                                    .breakLongLines()
                                    .forEach { line ->
                                        write("// #L", line)
                                    }

                                renderNormalExample(ctx, writer, op, example)
                            }
                        }
                }
                val contents = writer.toString()
                delegator.fileManifest.writeFile("src/samples/${op.id.name}.kt", contents)
            }
    }

    private fun renderNormalExample(ctx: CodegenContext, writer: KotlinWriter, op: OperationShape, example: Example) {
        val clientName = clientName(ctx.settings.sdkId).replaceFirstChar { it.lowercase(Locale.getDefault()) }
        val respPrefix = if (example.output.isPresent) "val resp = " else ""

        val input = ctx.model.expectShape(op.inputShape)
        if (input.hasAllOptionalMembers && example.input.isEmpty) {
            writer.write("#L#LClient.#L()", respPrefix, clientName, op.defaultName())
        } else {
            writer.withBlock("#L#LClient.#L {", "}", respPrefix, clientName, op.defaultName()) {
                ShapeValueGenerator(ctx.model, ctx.symbolProvider).writeShapeValues(writer, input, example.input)
            }
        }
    }
}

private val wordsPattern = Regex("""\w+[.,]?|".*?"[.,]?|\(.*\)[.,]?""")
internal fun String.breakLongLines(maxLineLengthChars: Int = 100): List<String> {
    val words = wordsPattern.findAll(this).map(MatchResult::value)
    val lines = mutableListOf<String>()
    val wordsOnLine = mutableListOf<String>()
    var lineLength = 0

    words.forEach { word ->
        if (word.length + lineLength < maxLineLengthChars) {
            if (wordsOnLine.isNotEmpty()) lineLength++
            lineLength += word.length
            wordsOnLine.add(word)
        } else {
            lines.add(wordsOnLine.joinToString(separator = " "))
            lineLength = 0
            wordsOnLine.clear()
            wordsOnLine.add(word)
        }
    }

    if (wordsOnLine.isNotEmpty()) {
        lines.add(wordsOnLine.joinToString(separator = " "))
    }
    return lines
}
