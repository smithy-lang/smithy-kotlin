/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.aws.protocols

import software.amazon.smithy.kotlin.codegen.protocols.AwsQuery
import software.amazon.smithy.kotlin.codegen.test.*
import kotlin.test.Test

class AwsQueryTest {
    @Test
    fun testNonNestedIdempotencyToken() {
        val ctx = model.newTestContext("Example")

        val generator = AwsQuery()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.bar?.let { field(BAR_DESCRIPTOR, it) } ?: field(BAR_DESCRIPTOR, context.idempotencyTokenProvider.generateToken())
            }
        """.trimIndent()

        val actual = ctx
            .manifest
            .expectFileString("/src/main/kotlin/com/test/serde/GetBarUnNestedOperationSerializer.kt")
            .lines("    serializer.serializeStruct(OBJ_DESCRIPTOR) {", "    }")
            .trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testNestedIdempotencyToken() {
        val ctx = model.newTestContext("Example")

        val generator = AwsQuery()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.baz?.let { field(BAZ_DESCRIPTOR, it) }
            }
        """.trimIndent()

        val actual = ctx
            .manifest
            .expectFileString("/src/main/kotlin/com/test/serde/NestDocumentSerializer.kt")
            .lines("    serializer.serializeStruct(OBJ_DESCRIPTOR) {", "    }")
            .trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)

        val unexpected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.baz?.let { field(BAZ_DESCRIPTOR, it) } ?: field(BAR_DESCRIPTOR, context.idempotencyTokenProvider.generateToken())
            }
        """.trimIndent()

        actual.shouldNotContainOnlyOnceWithDiff(unexpected)
    }

    private val model = """
        ${"$"}version: "2"
    
        namespace com.test
        
        use aws.protocols#awsQuery
        use aws.api#service
    
        @awsQuery
        @service(sdkId: "Example")
        @xmlNamespace(uri: "http://foo.com")
        service Example {
            version: "1.0.0",
            operations: [GetBarUnNested, GetBarNested]
        }
    
        @http(method: "POST", uri: "/get-bar-un-nested")
        operation GetBarUnNested {
            input: BarUnNested
        }
        
        structure BarUnNested {
            @idempotencyToken
            bar: String
        }
        
        @http(method: "POST", uri: "/get-bar-nested")
        operation GetBarNested {
            input: BarNested
        }
        
        structure BarNested {
            bar: Nest
        }
        
        structure Nest {
            @idempotencyToken
            baz: String
        }
    """.toSmithyModel()
}
