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

import java.util.logging.Logger
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.build.PluginContext
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.neighbor.Walker
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeVisitor
import software.amazon.smithy.model.shapes.StructureShape

/**
 * Orchestrates Kotlin code generation
 */
class CodegenVisitor(context: PluginContext) : ShapeVisitor.Default<Void>() {

    override fun getDefault(shape: Shape?): Void? {
        return null
    }

    val LOGGER = Logger.getLogger(javaClass.name)
    private val model = context.model
    private val modelWithoutTraits = context.modelWithoutTraitShapes
    private val settings = KotlinSettings.from(context.model, context.settings)
    private val service: ServiceShape = settings.getService(model)
    private val fileManifest: FileManifest = context.fileManifest
    private val symbolProvider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, settings.moduleName)
    private val writers: KotlinDelegator = KotlinDelegator(settings, model, fileManifest, symbolProvider)

    fun execute() {
        LOGGER.info("Generating Kotlin client for service ${settings.service}")

        LOGGER.info("Walking shapes from ${settings.service} to find shapes to generate")
        val serviceShapes = Walker(modelWithoutTraits).walkShapes(service)
        serviceShapes.forEach { it.accept(this) }

        println("flushing writers")
        writers.flushWriters()

        writeGradleBuild(settings, fileManifest)
    }

    override fun structureShape(shape: StructureShape): Void? {
        writers.useShapeWriter(shape) { StructureGenerator(model, symbolProvider, it, shape).render() }
        return null
    }

    override fun serviceShape(shape: ServiceShape?): Void? {
        writers.useShapeWriter(shape) {
            // TODO - generate client(s)
        }
        return null
    }
}
