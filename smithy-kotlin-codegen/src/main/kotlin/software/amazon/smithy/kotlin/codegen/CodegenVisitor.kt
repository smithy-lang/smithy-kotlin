/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.toRenderingContext
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.OperationNormalizer
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ApplicationProtocol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait
import software.amazon.smithy.model.transform.ModelTransformer
import java.util.*
import java.util.logging.Logger

/**
 * Orchestrates Kotlin code generation
 */
class CodegenVisitor(context: PluginContext) : ShapeVisitor.Default<Unit>() {

    private val LOGGER = Logger.getLogger(javaClass.name)
    private val model: Model
    private val settings = KotlinSettings.from(context.model, context.settings)
    private val service: ServiceShape
    private val fileManifest: FileManifest = context.fileManifest
    private val symbolProvider: SymbolProvider
    private val writers: KotlinDelegator
    private val integrations: List<KotlinIntegration>
    private val protocolGenerator: ProtocolGenerator?
    private val applicationProtocol: ApplicationProtocol
    private val baseGenerationContext: GenerationContext

    init {
        val classLoader = context.pluginClassLoader.orElse(javaClass.classLoader)
        LOGGER.info("Discovering KotlinIntegration providers...")
        integrations = ServiceLoader.load(KotlinIntegration::class.java, classLoader)
            .onEach { integration -> LOGGER.info("Loaded KotlinIntegration: ${integration.javaClass.name}") }
            .filter { integration -> integration.enabledForService(context.model, settings) }
            .onEach { integration -> LOGGER.info("Enabled KotlinIntegration: ${integration.javaClass.name}") }
            .sortedBy(KotlinIntegration::order)
            .toList()

        LOGGER.info("Preprocessing model")
        // Model pre-processing:
        // 1. Start with the model from the plugin context
        // 2. Apply integrations
        // 3. Flatten error shapes (see: https://github.com/awslabs/smithy/pull/919)
        // 4. Normalize the operations
        var resolvedModel = context.model
        for (integration in integrations) {
            resolvedModel = integration.preprocessModel(resolvedModel, settings)
        }

        resolvedModel = ModelTransformer.create()
            .copyServiceErrorsToOperations(resolvedModel, settings.getService(resolvedModel))

        // normalize operations
        model = OperationNormalizer.transform(resolvedModel, settings.service)

        service = settings.getService(model)

        symbolProvider = integrations.fold(
            KotlinCodegenPlugin.createSymbolProvider(model, settings)
        ) { provider, integration ->
            integration.decorateSymbolProvider(settings, model, provider)
        }

        writers = KotlinDelegator(settings, model, fileManifest, symbolProvider, integrations)

        protocolGenerator = resolveProtocolGenerator(integrations, model, service, settings)
        applicationProtocol = protocolGenerator?.applicationProtocol ?: ApplicationProtocol.createDefaultHttpApplicationProtocol()

        baseGenerationContext = GenerationContext(model, symbolProvider, settings, protocolGenerator, integrations)
    }

    private fun resolveProtocolGenerator(
        integrations: List<KotlinIntegration>,
        model: Model,
        service: ServiceShape,
        settings: KotlinSettings
    ): ProtocolGenerator? {
        val generators = integrations.flatMap { it.protocolGenerators }.associateBy { it.protocol }
        val serviceIndex = ServiceIndex.of(model)

        try {
            val protocolTrait = settings.resolveServiceProtocol(serviceIndex, service, generators.keys)
            return generators[protocolTrait]
        } catch (ex: UnresolvableProtocolException) {
            LOGGER.warning("Unable to find protocol generator for ${service.id}: ${ex.message}")
        }
        return null
    }

    fun execute() {
        LOGGER.info("Generating Kotlin client for service ${settings.service}")

        LOGGER.info("Walking shapes from ${settings.service} to find shapes to generate")
        val modelWithoutTraits = ModelTransformer.create().getModelWithoutTraitShapes(model)
        val serviceShapes = Walker(modelWithoutTraits).walkShapes(service)
        serviceShapes.forEach { it.accept(this) }

        protocolGenerator?.apply {
            val ctx = ProtocolGenerator.GenerationContext(
                settings,
                model,
                service,
                symbolProvider,
                integrations,
                protocol,
                writers
            )

            LOGGER.info("[${service.id}] Generating unit tests for protocol $protocol")
            generateProtocolUnitTests(ctx)

            LOGGER.info("[${service.id}] Generating service client for protocol $protocol")
            generateProtocolClient(ctx)
        }

        if (settings.build.generateDefaultBuildFiles) {
            val dependencies = writers.dependencies
                .mapNotNull { it.properties["dependency"] as? KotlinDependency }
                .distinct()
            writeGradleBuild(settings, fileManifest, dependencies)
        }

        // write files defined by integrations
        integrations.forEach { it.writeAdditionalFiles(baseGenerationContext, writers) }

        writers.flushWriters()
    }

    override fun getDefault(shape: Shape?) { }

    override fun structureShape(shape: StructureShape) {
        writers.useShapeWriter(shape) {
            val renderingContext = baseGenerationContext.toRenderingContext(it, shape)
            StructureGenerator(renderingContext).render()
        }
    }

    override fun stringShape(shape: StringShape) {
        if (shape.hasTrait<EnumTrait>()) {
            writers.useShapeWriter(shape) { EnumGenerator(shape, symbolProvider.toSymbol(shape), it).render() }
        }
    }

    override fun unionShape(shape: UnionShape) {
        writers.useShapeWriter(shape) { UnionGenerator(model, symbolProvider, it, shape).render() }
    }

    override fun serviceShape(shape: ServiceShape) {
        if (service != shape) {
            LOGGER.fine("Skipping `${shape.id}` because it is not `${service.id}`")
            return
        }

        writers.useShapeWriter(shape) {
            val renderingCtx = baseGenerationContext.toRenderingContext(it, shape)
            ServiceGenerator(renderingCtx).render()
        }

        // render the service (client) base exception type
        val baseExceptionSymbol = ExceptionBaseClassGenerator.baseExceptionSymbol(baseGenerationContext.settings)
        writers.useFileWriter("${baseExceptionSymbol.name}.kt", baseExceptionSymbol.namespace) { writer ->
            ExceptionBaseClassGenerator.render(baseGenerationContext, writer)
        }
    }
}
