/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.smithy.kotlin.codegen

import java.util.*
import java.util.logging.Logger
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.EnumTrait

/**
 * Orchestrates Kotlin code generation
 */
class CodegenVisitor(context: PluginContext) : ShapeVisitor.Default<Unit>() {

    private val LOGGER = Logger.getLogger(javaClass.name)
    private val model: Model
    private val modelWithoutTraits = context.modelWithoutTraitShapes
    private val settings = KotlinSettings.from(context.model, context.settings)
    private val service: ServiceShape
    private val fileManifest: FileManifest = context.fileManifest
    private val symbolProvider: SymbolProvider
    private val writers: KotlinDelegator
    private val integrations: List<KotlinIntegration>
    private val protocolGenerator: ProtocolGenerator?
    private val applicationProtocol: ApplicationProtocol

    init {
        LOGGER.info("Attempting to discover KotlinIntegration from classpath...")
        integrations = ServiceLoader.load(KotlinIntegration::class.java, context.pluginClassLoader.orElse(javaClass.classLoader))
            .also { integration ->
                LOGGER.info("Adding KotlinIntegration: ${integration.javaClass.name}")
            }.sortedBy(KotlinIntegration::order).toList()

        LOGGER.info("Preprocessing model")
        var resolvedModel = context.model
        for (integration in integrations) {
            resolvedModel = integration.preprocessModel(resolvedModel, settings)
        }
        model = resolvedModel

        service = settings.getService(model)
        symbolProvider = integrations.fold(
            KotlinCodegenPlugin.createSymbolProvider(model, settings.moduleName)
        ) { provider, integration ->
            integration.decorateSymbolProvider(settings, model, provider)
        }

        writers = KotlinDelegator(settings, model, fileManifest, symbolProvider, integrations)

        protocolGenerator = resolveProtocolGenerator(integrations, model, service, settings)
        applicationProtocol = protocolGenerator?.applicationProtocol ?: ApplicationProtocol.createDefaultHttpApplicationProtocol()
    }

    private fun resolveProtocolGenerator(
        integrations: List<KotlinIntegration>,
        model: Model,
        service: ServiceShape,
        settings: KotlinSettings
    ): ProtocolGenerator? {
        val generators = integrations.flatMap { it.protocolGenerators }.associateBy { it.protocol }
        val serviceIndex = model.getKnowledge(ServiceIndex::class.java)

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

            LOGGER.info("[${service.id}] Generating serde for protocol $protocol")
            generateSerializers(ctx)
            generateDeserializers(ctx)

            LOGGER.info("[${service.id}] Generating unit tests for protocol $protocol")
            generateProtocolUnitTests(ctx)

            LOGGER.info("[${service.id}] Generating service client for protocol $protocol")
            generateProtocolClient(ctx)
        }

        val dependencies = writers.dependencies.map { it.properties["dependency"] as KotlinDependency }.distinct()
        writeGradleBuild(settings, fileManifest, dependencies, integrations)

        println("flushing writers")
        writers.flushWriters()
    }

    override fun getDefault(shape: Shape?) {
    }

    override fun structureShape(shape: StructureShape) {
        writers.useShapeWriter(shape) { StructureGenerator(model, symbolProvider, it, shape, protocolGenerator).render() }
    }

    override fun stringShape(shape: StringShape) {
        if (shape.hasTrait(EnumTrait::class.java)) {
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
            ServiceGenerator(model, symbolProvider, it, shape, settings.moduleName, applicationProtocol).render()
        }
    }
}
