/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
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
import software.amazon.smithy.kotlin.codegen.model.getEndpointRules
import software.amazon.smithy.kotlin.codegen.model.getEndpointTests
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.*
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ApplicationProtocol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*
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
            .filter { integration -> integration.enabledForService(context.model, settings) } // TODO: Change so we don't filter until previous integrations model modifications are complete
            .onEach { integration -> LOGGER.info("Enabled KotlinIntegration: ${integration.javaClass.name}") }
            .sortedBy(KotlinIntegration::order)

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
            KotlinCodegenPlugin.createSymbolProvider(model, settings),
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
        settings: KotlinSettings,
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
                writers,
            )

            LOGGER.info("[${service.id}] Generating unit tests for protocol $protocol")
            generateProtocolUnitTests(ctx)

            LOGGER.info("[${service.id}] Generating service client for protocol $protocol")
            generateProtocolClient(ctx)

            LOGGER.info("[${service.id}] Generating endpoint provider for protocol $protocol")
            generateEndpointsSources(ctx)

            LOGGER.info("[${service.id}] Generating auth scheme provider for protocol $protocol")
            generateAuthSchemeProvider(ctx)
        }

        writers.finalize()

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
        // smithy will present both strings with legacy enum trait AND explicit (non-int) enum shapes in this manner
        if (shape.hasTrait<@Suppress("DEPRECATION") software.amazon.smithy.model.traits.EnumTrait>()) {
            writers.useShapeWriter(shape) { EnumGenerator(shape, symbolProvider.toSymbol(shape), it).render() }
        }
    }

    override fun intEnumShape(shape: IntEnumShape) {
        writers.useShapeWriter(shape) { EnumGenerator(shape, symbolProvider.toSymbol(shape), it).render() }
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
            ServiceClientGenerator(renderingCtx).render()
        }

        // render the service (client) base exception type
        val baseExceptionSymbol = ExceptionBaseClassGenerator.baseExceptionSymbol(baseGenerationContext.settings)
        writers.useFileWriter("${baseExceptionSymbol.name}.kt", baseExceptionSymbol.namespace) { writer ->
            ExceptionBaseClassGenerator.render(baseGenerationContext, writer)
        }
    }
}

// delegate generation of endpoints-related modules
// the following are generated regardless of whether the model has endpoint rules:
// - typed EndpointProvider interface
// - EndpointParameters struct (will just be empty if no rules/params)
// - ResolveEndpointMiddleware
// the actual default implementation and test cases will only be generated if there's an actual rule set from which to
// derive them
private fun ProtocolGenerator.generateEndpointsSources(ctx: ProtocolGenerator.GenerationContext) {
    with(endpointDelegator(ctx)) {
        val rules = ctx.service.getEndpointRules()
        generateEndpointProvider(ctx, rules)
        generateEndpointParameters(ctx, rules)
        generateEndpointResolverAdapter(ctx)
        if (rules != null) {
            generateEndpointProviderTests(ctx, ctx.service.getEndpointTests(), rules)
        }
    }
}

private fun ProtocolGenerator.generateAuthSchemeProvider(ctx: ProtocolGenerator.GenerationContext) {
    with(authSchemeDelegator(ctx)) {
        identityProviderGenerator().render(ctx)
        authSchemeParametersGenerator().render(ctx)
        authSchemeProviderGenerator().render(ctx)
        authSchemeProviderAdapterGenerator().render(ctx)
    }
}
