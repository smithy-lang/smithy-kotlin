/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model
import kotlin.test.Test

// NOTE: protocol conformance is mostly handled by the protocol tests suite
class IdempotentTokenGeneratorTest {
    private val defaultModel: Model = javaClass.getResource("idempotent-token-test-model.smithy").toSmithyModel()

    private fun getTransformFileContents(filename: String): String {
        val (ctx, manifest, generator) = defaultModel.newTestContext()
        generator.generateSerializers(ctx)
        generator.generateDeserializers(ctx)
        ctx.delegator.flushWriters()
        return manifest.getTransformFileContents(filename)
    }

    // Assume a specific file path to retrieve a file from the manifest
    private fun MockManifest.getTransformFileContents(filename: String, packageNamespace: String = TestModelDefault.NAMESPACE): String {
        val packageNamespaceExpr = packageNamespace.replace('.', '/')
        return expectFileString("src/main/kotlin/$packageNamespaceExpr/transform/$filename")
    }

    @Test
    fun `it serializes operation payload inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
            internal class AllocateWidgetOperationSerializer: HttpSerialize<AllocateWidgetRequest> {
                override suspend fun serialize(context: ExecutionContext, input: AllocateWidgetRequest): HttpRequestBuilder {
                    val builder = HttpRequestBuilder()
                    builder.method = HttpMethod.POST
            
                    builder.url {
                        path = "/input/AllocateWidget"
                    }
            
                    val payload = serializeAllocateWidgetOperationBody(context, input)
                    builder.body = ByteArrayContent(payload)
                    if (builder.body !is HttpBody.Empty) {
                        builder.headers.setMissing("Content-Type", "application/json")
                    }
                    return builder
                }
            }
        """.trimIndent()
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it serializes operation query inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetQueryOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class AllocateWidgetQueryOperationSerializer: HttpSerialize<AllocateWidgetQueryRequest> {
    override suspend fun serialize(context: ExecutionContext, input: AllocateWidgetQueryRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/AllocateWidgetQuery"
            parameters {
                append("clientToken", (input.clientToken ?: context.idempotencyTokenProvider.generateToken()))
            }
        }

        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }

    @Test
    fun `it serializes operation header inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetHeaderOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
internal class AllocateWidgetHeaderOperationSerializer: HttpSerialize<AllocateWidgetHeaderRequest> {
    override suspend fun serialize(context: ExecutionContext, input: AllocateWidgetHeaderRequest): HttpRequestBuilder {
        val builder = HttpRequestBuilder()
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/AllocateWidgetHeader"
        }

        builder.headers {
            append("clientToken", (input.clientToken ?: context.idempotencyTokenProvider.generateToken()))
        }

        return builder
    }
}
"""
        contents.shouldContainOnlyOnceWithDiff(expectedContents)
    }
}
