/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.model.knowledge

import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.kotlin.codegen.rendering.auth.AnonymousAuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.model.knowledge.ServiceIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.AuthTrait
import software.amazon.smithy.model.traits.OptionalAuthTrait

/**
 * Knowledge index for dealing with authentication traits and AuthSchemeHandlers (e.g. preserving correct order,
 * dealing with defaults, etc).
 */
class AuthIndex {
    /**
     * Get the Map of [AuthSchemeHandler]'s registered. The returned map is de-duplicated by
     * scheme ID with the last integration taking precedence. This map is not yet reconciled with the
     * auth schemes used by the model.
     */
    fun authHandlers(ctx: ProtocolGenerator.GenerationContext): Map<ShapeId, AuthSchemeHandler> =
        ctx.integrations
            .flatMap { it.authSchemes(ctx) }
            .associateBy(AuthSchemeHandler::authSchemeId)

    /**
     * Get the prioritized list of effective [AuthSchemeHandler] for an operation.
     *
     * @param ctx the generation context
     * @param op the operation to get auth handlers for
     * @return the prioritized list of handlers for [op]
     */
    fun effectiveAuthHandlersForOperation(ctx: ProtocolGenerator.GenerationContext, op: OperationShape): List<AuthSchemeHandler> {
        val serviceIndex = ServiceIndex.of(ctx.model)
        val allAuthHandlers = authHandlers(ctx)

        // anonymous auth (optionalAuth trait) is handled as an annotation trait...
        val opEffectiveAuthSchemes = serviceIndex.getEffectiveAuthSchemes(ctx.service, op)
        return if (op.hasTrait<OptionalAuthTrait>() || opEffectiveAuthSchemes.isEmpty()) {
            listOf(AnonymousAuthSchemeHandler())
        } else {
            // return handlers in same order as the priority list dictated by `auth([])` trait
            opEffectiveAuthSchemes.mapNotNull {
                allAuthHandlers[it.key]
            }
        }
    }

    /**
     * Get the prioritized list of effective [AuthSchemeHandler] for a service (auth handlers reconciled with the
     * `auth([]` trait).
     *
     * @param ctx the generation context
     * @return the prioritized list of handlers for [ProtocolGenerator.GenerationContext.service]
     */
    fun effectiveAuthHandlersForService(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
        val serviceIndex = ServiceIndex.of(ctx.model)
        val allAuthHandlers = authHandlers(ctx)

        val effectiveAuthSchemes = serviceIndex.getEffectiveAuthSchemes(ctx.service)
            .takeIf { it.isNotEmpty() } ?: listOf(AnonymousAuthSchemeHandler()).associateBy(AuthSchemeHandler::authSchemeId)

        return effectiveAuthSchemes.mapNotNull {
            allAuthHandlers[it.key]
        }
    }

    /**
     * Get the list of [AuthSchemeHandler] for a service (auth handlers reconciled with the
     * all possible authentication traits applied to the service).
     *
     * @param ctx the generation context
     * @return the list of handlers for [ProtocolGenerator.GenerationContext.service]
     */
    fun authHandlersForService(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
        val serviceIndex = ServiceIndex.of(ctx.model)
        val allAuthHandlers = authHandlers(ctx)

        // all auth schemes possible on the service (this does not handle optional/anonymous auth)
        val allAuthSchemes = serviceIndex.getAuthSchemes(ctx.service)
        val handlers = mutableListOf<AuthSchemeHandler>()
        allAuthSchemes.mapNotNullTo(handlers) {
            allAuthHandlers[it.key]
        }

        // reconcile anonymous auth
        val topDownIndex = TopDownIndex.of(ctx.model)
        val addAnonymousHandler = topDownIndex.getContainedOperations(ctx.service)
            .any { op ->
                val opEffectiveAuthSchemes = serviceIndex.getEffectiveAuthSchemes(ctx.service, op)
                op.hasTrait<OptionalAuthTrait>() || opEffectiveAuthSchemes.isEmpty()
            }

        if (addAnonymousHandler) {
            handlers.add(AnonymousAuthSchemeHandler())
        }

        return handlers
    }

    /**
     * Get the set of operations that need overridden in the generated auth scheme resolver.
     */
    fun operationsWithOverrides(ctx: ProtocolGenerator.GenerationContext): Set<OperationShape> {
        val topDownIndex = TopDownIndex.of(ctx.model)

        val operations = topDownIndex.getContainedOperations(ctx.service)
        val operationsWithOverrides = operations.filter { op ->
            op.hasTrait<AuthTrait>() || op.hasTrait<UnsignedPayloadTrait>() || op.hasTrait<OptionalAuthTrait>()
        }

        return operationsWithOverrides.toSet()
    }
}
