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
 * Common codegen properties required across different codegen contexts
 */
interface CodegenContext {
    val model: Model
    val symbolProvider: SymbolProvider
    val settings: KotlinSettings
    val protocolGenerator: ProtocolGenerator?
    val integrations: List<KotlinIntegration>
    val rootNamespace: String
}

/**
 * Base generation context
 */
data class GenerationContext(
    override val model: Model,
    override val symbolProvider: SymbolProvider,
    override val settings: KotlinSettings,
    override val protocolGenerator: ProtocolGenerator? = null,
    override val integrations: List<KotlinIntegration> = listOf(),
    override val rootNamespace: String = settings.rootNamespace,
) : CodegenContext

/**
 * Convert this context into a context for rendering a specific shape
 */
fun <T : Shape> CodegenContext.toRenderingContext(writer: KotlinWriter, forShape: T? = null): RenderingContext<T> =
    RenderingContext(this, writer, forShape)

/**
 * Context passed to an individual generator for rendering (writing) a shape
 */
data class RenderingContext<T : Shape>(
    // writer to render to
    val writer: KotlinWriter,
    // shape to render for
    val shape: T?,
    override val model: Model,
    override val symbolProvider: SymbolProvider,
    override val settings: KotlinSettings,
    override val protocolGenerator: ProtocolGenerator? = null,
    override val integrations: List<KotlinIntegration> = listOf(),
    // override the root package name
    override val rootNamespace: String = settings.rootNamespace,
) : CodegenContext {

    constructor(otherCtx: CodegenContext, writer: KotlinWriter, shape: T?) :
        this(writer, shape, otherCtx.model, otherCtx.symbolProvider, otherCtx.settings, otherCtx.protocolGenerator, otherCtx.integrations, otherCtx.rootNamespace)
}
