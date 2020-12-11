/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.Shape

/**
 * Common properties required across codegen
 */
open class GenerationContext(
    val model: Model,
    val symbolProvider: SymbolProvider,
    val rootNamespace: String,
    val protocolGenerator: ProtocolGenerator?,
    val integrations: List<KotlinIntegration> = listOf()
) {
    /**
     * Convert this context into a context for rendering a specific shape
     */
    fun toRenderingContext(writer: KotlinWriter, forShape: Shape? = null): RenderingContext =
        RenderingContext(model, symbolProvider, writer, forShape, rootNamespace, protocolGenerator, integrations)
}

/**
 * Context passed to an individual generator for rendering (writing) a shape
 */
class RenderingContext(
    model: Model,
    symbolProvider: SymbolProvider,
    // writer to render to
    val writer: KotlinWriter,
    // shape to render for
    val shape: Shape?,
    rootNamespace: String,
    protocolGenerator: ProtocolGenerator? = null,
    integrations: List<KotlinIntegration> = listOf()
) : GenerationContext(model, symbolProvider, rootNamespace, protocolGenerator, integrations)
