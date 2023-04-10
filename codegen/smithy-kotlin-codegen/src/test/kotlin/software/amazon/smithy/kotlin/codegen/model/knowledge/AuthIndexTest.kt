/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.model.knowledge

import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.integration.AuthSchemeHandler
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.loadModelFromResource
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.auth.AnonymousAuthSchemeIntegration
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.newTestContext
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.HttpApiKeyAuthTrait
import software.amazon.smithy.model.traits.HttpBasicAuthTrait
import software.amazon.smithy.model.traits.HttpBearerAuthTrait
import software.amazon.smithy.model.traits.OptionalAuthTrait
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthIndexTest {
    private class TestAuthSchemeHandler(
        override val authSchemeId: ShapeId,
        val testId: String? = null,
    ): AuthSchemeHandler {
        override fun instantiateAuthSchemeExpr(ctx: ProtocolGenerator.GenerationContext, writer: KotlinWriter) { error("not needed for test") }

        override fun identityProviderAdapterExpression(writer: KotlinWriter) { error("not needed for test") }

        override fun authSchemeProviderInstantiateAuthOptionExpr(
            ctx: ProtocolGenerator.GenerationContext,
            op: OperationShape?,
            writer: KotlinWriter
        ) { error("not needed for test") }
    }

    // mock out the http auth integrations
    val mockIntegrations = listOf<KotlinIntegration>(
        object : KotlinIntegration {
            override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
                return listOf(TestAuthSchemeHandler(HttpApiKeyAuthTrait.ID))
            }
        },
        object : KotlinIntegration {
            override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
                return listOf(TestAuthSchemeHandler(HttpBasicAuthTrait.ID))
            }
        },
        object : KotlinIntegration {
            override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
                return listOf(TestAuthSchemeHandler(HttpBearerAuthTrait.ID))
            }
        },
        AnonymousAuthSchemeIntegration()
    )


    @Test
    fun testAuthHandlersDedup() {
        val model = loadModelFromResource("service-auth-test.smithy")
        val i1 = object : KotlinIntegration {
            override val order: Byte = -10
            override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
                return listOf(TestAuthSchemeHandler(HttpApiKeyAuthTrait.ID, "integration 1"))
            }
        }

        val i2 = object : KotlinIntegration {
            override val order: Byte = 20
            override fun authSchemes(ctx: ProtocolGenerator.GenerationContext): List<AuthSchemeHandler> {
                return listOf(TestAuthSchemeHandler(HttpApiKeyAuthTrait.ID, "integration 2"))
            }
        }

        val testCtx = model.newTestContext(integrations = listOf(i1, i2))
        val authIndex = AuthIndex()
        val handlers = authIndex.authHandlers(testCtx.generationCtx).values.toList()
        assertEquals(1, handlers.size)
        assertEquals("integration 2", (handlers[0] as TestAuthSchemeHandler).testId)
    }

    @Test
    fun testEffectiveOperationHandlers() {
        val model = loadModelFromResource("service-auth-test.smithy")
        val testCtx = model.newTestContext(integrations = mockIntegrations)

        val authIndex = AuthIndex()

        val tests = listOf(
            "com.test#GetFooServiceDefault" to listOf(HttpApiKeyAuthTrait.ID),
            "com.test#GetFooOpOverride" to listOf(HttpBasicAuthTrait.ID, HttpBearerAuthTrait.ID),
            "com.test#GetFooAnonymous" to listOf(OptionalAuthTrait.ID),
            "com.test#GetFooOptionalAuth" to listOf(OptionalAuthTrait.ID),
        )

        tests.forEach { (opShapeId, expectedSchemes) ->
            val op = model.expectShape<OperationShape>(opShapeId)
            val handlers = authIndex.effectiveAuthHandlersForOperation(testCtx.generationCtx, op)
            val actualSchemes = handlers.map { it.authSchemeId }
            assertEquals(expectedSchemes, actualSchemes)
        }
    }

    @Test
    fun testEffectiveServiceHandlers() {
        val model = loadModelFromResource("service-auth-test.smithy")
        val testCtx = model.newTestContext(integrations = mockIntegrations)
        val authIndex = AuthIndex()

        val handlers = authIndex.effectiveAuthHandlersForService(testCtx.generationCtx)
        val actual = handlers.map { it.authSchemeId }
        val expected = listOf(HttpApiKeyAuthTrait.ID)
        assertEquals(expected, actual)
    }

    @Test
    fun testEffectiveServiceHandlersWithNoAuth() {
        val model = loadModelFromResource("simple-service.smithy")
        val testCtx = model.newTestContext(integrations = mockIntegrations)
        val authIndex = AuthIndex()

        val handlers = authIndex.effectiveAuthHandlersForService(testCtx.generationCtx)
        val actual = handlers.map { it.authSchemeId }
        val expected = listOf(OptionalAuthTrait.ID)
        assertEquals(expected, actual)
    }

    @Test
    fun testServiceHandlers() {
        val model = loadModelFromResource("service-auth-test.smithy")
        val testCtx = model.newTestContext(integrations = mockIntegrations)
        val authIndex = AuthIndex()

        val handlers = authIndex.authHandlersForService(testCtx.generationCtx)
        val actual = handlers.map { it.authSchemeId }.toSet()
        val expected = setOf(HttpApiKeyAuthTrait.ID, HttpBearerAuthTrait.ID, HttpBasicAuthTrait.ID, OptionalAuthTrait.ID)
        assertEquals(expected, actual)
    }

    @Test
    fun testOperationsWithOverrides() {
        val model = loadModelFromResource("service-auth-test.smithy")
        val testCtx = model.newTestContext(integrations = mockIntegrations)
        val authIndex = AuthIndex()

        val actual = authIndex.operationsWithOverrides(testCtx.generationCtx)
        val expected = setOf(
            model.expectShape<OperationShape>("com.test#GetFooOpOverride"),
            model.expectShape<OperationShape>("com.test#GetFooAnonymous"),
            model.expectShape<OperationShape>("com.test#GetFooOptionalAuth"),
            model.expectShape<OperationShape>("com.test#GetFooUnsigned"),
        )
        assertEquals(expected, actual)
    }
}