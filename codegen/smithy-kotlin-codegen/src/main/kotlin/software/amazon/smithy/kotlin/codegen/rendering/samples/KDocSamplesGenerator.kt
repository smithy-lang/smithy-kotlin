/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.samples

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.ShapeValueGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.traits.ExamplesTrait
import software.amazon.smithy.model.traits.ExamplesTrait.Example
import software.amazon.smithy.model.transform.ModelTransformer
import java.util.*
import kotlin.jvm.optionals.getOrDefault

// FIXME - add to default integrations (after documentation pre-processor)

// FIXME: This renders using the playground by default which won't actually work
//  see https://github.com/Kotlin/dokka/issues/3041
//  Probably hold off merging until fixed

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
        return transformer.mapTraits(model) { shape, trait ->
            when {
                shape is OperationShape && shape.hasTrait<ExamplesTrait>() && trait is DocumentationTrait -> {
                    val examplesTrait = shape.expectTrait<ExamplesTrait>()
                    val kdocSampleIdentifiers =
                        (0 until examplesTrait.examples.size).joinToString(separator = "\n") { idx ->
                            val identifier = sampleIdentifier(settings, shape, idx)
                            "@sample $identifier"
                        }

                    val updatedDocs = trait.value + "\n\n" + kdocSampleIdentifiers
                    DocumentationTrait(updatedDocs, trait.sourceLocation)
                }
                else -> trait
            }
        }
    }

    private fun sampleIdentifier(settings: KotlinSettings, op: OperationShape, index: Int): String =
        listOf(
            samplePackage(settings),
            sampleClassName(op),
            sampleFunctionName(index),
        ).joinToString(separator = ".")

    private fun sampleFunctionName(index: Int): String = "sample${index + 1}"
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
                    examples.examples.forEachIndexed { idx, example ->
                        write("")
                        write("@Sample")
                        withBlock("fun #L() {", "}", sampleFunctionName(idx)) {
                            val docs = example.documentation.getOrDefault(example.title)
                            // TODO - cleanup formatting on docs
                            write("// #L", docs)
                            if (example.error.isPresent) {
                                renderErrorExample()
                            } else {
                                renderNormalExample(ctx, writer, op, example)
                            }
                        }
                        write("")
                    }
                }
                val contents = writer.toString()
                delegator.fileManifest.writeFile("samples/${op.id.name}.kt", contents)
            }
    }

    private fun renderNormalExample(ctx: CodegenContext, writer: KotlinWriter, op: OperationShape, example: Example) {
        val clientName = clientName(ctx.settings.sdkId).replaceFirstChar { it.lowercase(Locale.getDefault()) }
        val respPrefix = if (example.output.isPresent) "val resp = " else ""

        writer.withBlock("#L#LClient.#L {", "}", respPrefix, clientName, op.defaultName()) {
            val input = ctx.model.expectShape(op.inputShape)
            ShapeValueGenerator(ctx.model, ctx.symbolProvider).writeShapeValues(writer, input, example.input)
        }

        // TODO - echo outputs if applicable
    }

    private fun renderErrorExample() {
        // TODO - error samples
    }
}
