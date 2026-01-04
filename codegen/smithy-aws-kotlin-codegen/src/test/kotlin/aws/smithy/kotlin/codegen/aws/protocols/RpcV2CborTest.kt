/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.aws.protocols

import io.kotest.matchers.string.shouldNotContain
import aws.smithy.kotlin.codegen.test.lines
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.shouldNotContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
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
            operations: [GetFoo, GetFooStreaming, PutFooStreaming, GetBarUnNested, GetBarNested]
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
        
        @http(method: "POST", uri: "/put-foo-streaming")
        operation PutFooStreaming {
            input: PutFooStreamingInput
        }
        
        structure PutFooStreamingInput {
            room: String
            messages: PublishEvents
        }
        
        // Model taken from https://smithy.io/2.0/spec/streaming.html#event-streams
        @streaming
        union PublishEvents {
            message: Message
            leave: LeaveEvent
        }
        
        structure Message {
            message: String
        }
        
        structure LeaveEvent {}
        
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

    @Test
    fun testStandardAcceptHeader() {
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val actual = ctx.manifest.expectFileString("/src/main/kotlin/com/test/DefaultTestClient.kt")
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

    @Test
    fun testEventStreamContentTypeHeaders() {
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val serializer = ctx.manifest.expectFileString("/src/main/kotlin/com/test/serde/PutFooStreamingOperationSerializer.kt")

        // Event stream messages should have `:content-type: application/cbor`
        val encodeMessage = serializer.lines("private fun encodePutFooStreamingPublishEventsEventMessage(input: PublishEvents): Message = buildMessage {", "}")
        encodeMessage.shouldContainOnlyOnceWithDiff(
            """
            addHeader(":content-type", HeaderValue.String("application/cbor"))
            """.trimIndent(),
        )

        // Event stream requests should have Content-Type=application/vnd.amazon.eventstream
        val serializeBody = serializer.lines("    override suspend fun serialize(context: ExecutionContext, input: PutFooStreamingRequest): HttpRequestBuilder {", "}")
        serializeBody.shouldContainOnlyOnceWithDiff("""builder.headers.setMissing("Content-Type", "application/vnd.amazon.eventstream")""")
    }

    @Test
    fun testEventStreamInitialRequestDoesNotSerializeStreamMember() {
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val documentSerializer = ctx.manifest.expectFileString("/src/main/kotlin/com/test/serde/PutFooStreamingRequestDocumentSerializer.kt")

        val serializeBody = documentSerializer.lines("    serializer.serializeStruct(OBJ_DESCRIPTOR) {", "}")
        serializeBody.shouldNotContain("input.messages") // `messages` is the stream member and should not be serialized in the initial request
    }

    @Test
    fun testNonNestedIdempotencyToken() {
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
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
        val ctx = model.newTestContext("CborExample")

        val generator = RpcV2Cbor()
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
}
