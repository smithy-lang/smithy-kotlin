/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.Model

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

    @Test
    fun `it serializes operation payload inputs with idempotency token trait`() {
        val contents = getTransformFileContents("AllocateWidgetOperationSerializer.kt")
        contents.assertBalancedBracesAndParens()
        val expectedContents = """
            internal class AllocateWidgetOperationSerializer(): HttpSerialize<AllocateWidgetRequest> {
            
                companion object {
                    private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                        field(CLIENTTOKEN_DESCRIPTOR)
                    }
                }
            
                override suspend fun serialize(context: ExecutionContext, input: AllocateWidgetRequest): HttpRequestBuilder {
                    val builder = HttpRequestBuilder()
                    builder.method = HttpMethod.POST
            
                    builder.url {
                        path = "/input/AllocateWidget"
                    }
            
                    val serializer = context.serializer()
                    serializer.serializeStruct(OBJ_DESCRIPTOR) {
                        input.clientToken?.let { field(CLIENTTOKEN_DESCRIPTOR, it) } ?: field(CLIENTTOKEN_DESCRIPTOR, context.idempotencyTokenProvider.generateToken())
                    }
            
                    builder.body = ByteArrayContent(serializer.toByteArray())
                    if (builder.body !is HttpBody.Empty) {
                        builder.headers["Content-Type"] = "application/json"
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
internal class AllocateWidgetQueryOperationSerializer(): HttpSerialize<AllocateWidgetQueryRequest> {
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
internal class AllocateWidgetHeaderOperationSerializer(): HttpSerialize<AllocateWidgetHeaderRequest> {
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
