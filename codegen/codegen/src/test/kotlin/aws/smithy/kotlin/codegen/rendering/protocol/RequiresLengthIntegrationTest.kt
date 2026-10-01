/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.protocol

import aws.smithy.kotlin.codegen.core.KotlinWriter
import aws.smithy.kotlin.codegen.test.*
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class RequiresLengthIntegrationTest {
    private val modelWithRequiresLength = """
        @http(method: "PUT", uri: "/upload")
        operation Upload {
            input: UploadInput
        }

        structure UploadInput {
            @httpPayload
            @required
            body: FiniteStreamingBlob
        }

        @streaming
        @requiresLength
        blob FiniteStreamingBlob
    """.prependNamespaceAndService(
        protocol = AwsProtocolModelDeclaration.REST_JSON,
        operations = listOf("Upload"),
    ).toSmithyModel()

    private val modelWithoutRequiresLength = """
        @http(method: "PUT", uri: "/upload")
        operation Upload {
            input: UploadInput
        }

        structure UploadInput {
            @httpPayload
            @required
            body: StreamingBlob
        }

        @streaming
        blob StreamingBlob
    """.prependNamespaceAndService(
        protocol = AwsProtocolModelDeclaration.REST_JSON,
        operations = listOf("Upload"),
    ).toSmithyModel()

    @Test
    fun `it adds RequiresLengthInterceptor for operations with requiresLength`() {
        val ctx = modelWithRequiresLength.newTestContext(integrations = listOf(RequiresLengthIntegration()))
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val httpGenerator = ctx.generator as HttpBindingProtocolGenerator
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            httpGenerator.getHttpMiddleware(ctx.generationCtx),
            HttpTraitResolver(ctx.generationCtx, "application/json"),
        )
        generator.render(writer)
        val contents = writer.toString()
        assertContains(contents, "RequiresLengthInterceptor()")
    }

    @Test
    fun `it does not add RequiresLengthInterceptor without requiresLength`() {
        val ctx = modelWithoutRequiresLength.newTestContext(integrations = listOf(RequiresLengthIntegration()))
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        val httpGenerator = ctx.generator as HttpBindingProtocolGenerator
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            httpGenerator.getHttpMiddleware(ctx.generationCtx),
            HttpTraitResolver(ctx.generationCtx, "application/json"),
        )
        generator.render(writer)
        val contents = writer.toString()
        assertFalse(contents.contains("RequiresLengthInterceptor"))
    }
}
