/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId

/**
 * A type responsible for handling registration and codegen of a particular authentication scheme ID
 */
interface AuthSchemeHandler {
    /**
     * The auth scheme ID
     */
    val authSchemeId: ShapeId

    /**
     * Optional symbol in the runtime that this scheme ID is mapped to (e.g. `AuthSchemeId.Sigv4`)
     */
    val authSchemeIdSymbol: Symbol?
        get() = null

    /**
     * Render the expression mapping auth scheme ID to the SDK client config. This is used to render the
     * `IdentityProviderConfig` implementation.
     *
     * e.g. `config.credentialsProvider`
     * @return the expression to render
     */
    fun identityProviderAdapterExpression(writer: KotlinWriter)

    /**
     * Render code that instantiates an `AuthSchemeOption` for the generated auth scheme provider.
     *
     * @param ctx the protocol generator context
     * @param op optional operation shape to customize creation for
     * @return the expression to render
     */
    fun authSchemeProviderInstantiateAuthOptionExpr(ctx: ProtocolGenerator.GenerationContext, op: OperationShape? = null, writer: KotlinWriter)

    /**
     * Render any additional helper methods needed in the generated auth scheme provider
     */
    fun authSchemeProviderRenderAdditionalMethods(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) {}

    /**
     * Render code that instantiates the actual `HttpAuthScheme` for the generated service client implementation.
     *
     * @param ctx the protocol generator context
     * @return the expression to render
     */
    fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter)
}