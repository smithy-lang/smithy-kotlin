/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.aws.protocols

import software.amazon.smithy.kotlin.codegen.test.*
import kotlin.test.Test

class RpcV2CborTest {
    val model = """
        ${"$"}version: "2"

        namespace com.test
        
        use smithy.protocols#rpcv2Cbor
        use aws.api#service

        @rpcv2Cbor
        @service(sdkId: "CborExample")
        service CborExample {
            version: "1.0.0",
            operations: [GetFoo, GetFooStreaming]
        }

        @http(method: "POST", uri: "/foo")
        operation GetFoo {}
        
        @http(method: "POST", uri: "/foo-streaming")
        operation GetFooStreaming {
            input := {}
            output := {
                events: FooEvents
            }
        }
        
        // Model taken from https://smithy.io/2.0/spec/streaming.html#event-streams
        @streaming
        union FooEvents {
            up: Movement
            down: Movement
            left: Movement
            right: Movement
            throttlingError: ThrottlingError
        }
        
        structure Movement {
           velocity: Float
        }
        
        @error("client")
        @retryable(throttling: true)
        structure ThrottlingError {}
        """.toSmithyModel()

    @Test
    fun testStandardAcceptHeader() {
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val actual = ctx.manifest.expectFileString("/src/main/kotlin/com/test/DefaultTestClient.kt")
        println(actual)
        val getFooMethod = actual.lines("    override suspend fun getFoo(input: GetFooRequest): GetFooResponse {", "    }")

        val expectedHeaderMutation = """
        op.install(
            MutateHeaders().apply {
                append("Accept", "application/cbor")
            }
        )
        """.replaceIndent("        ")
        getFooMethod.shouldContainOnlyOnceWithDiff(expectedHeaderMutation)
    }

    @Test
    fun testEventStreamAcceptHeader() {
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val actual = ctx.manifest.expectFileString("/src/main/kotlin/com/test/DefaultTestClient.kt")
        println(actual)
        val getFooMethod = actual.lines("    override suspend fun <T> getFooStreaming(input: GetFooStreamingRequest, block: suspend (GetFooStreamingResponse) -> T): T {", "    }")

        val expectedHeaderMutation = """
        op.install(
            MutateHeaders().apply {
                append("Accept", "application/vnd.amazon.eventstream")
            }
        )
        """.replaceIndent("        ")
        getFooMethod.shouldContainOnlyOnceWithDiff(expectedHeaderMutation)
    }
}