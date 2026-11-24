/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.protocols.eventstream

import aws.smithy.kotlin.codegen.protocols.RpcV2Cbor
import aws.smithy.kotlin.codegen.test.lines
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.toSmithyModel
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import software.amazon.smithy.model.Model
import kotlin.test.Test

class EventStreamSerializerGeneratorTest {
    private fun model(sigv4: Boolean): Model = """
        ${"$"}version: "2"

        namespace com.test

        use smithy.protocols#rpcv2Cbor
        use aws.api#service
        ${if (sigv4) "use aws.auth#sigv4" else ""}

        @rpcv2Cbor
        @service(sdkId: "CborExample")
        ${if (sigv4) """@sigv4(name: "cborexample")""" else ""}
        service CborExample {
            version: "1.0.0",
            operations: [PutWithInitialMembers, PutStreamOnly, PutStreamOnlyWithoutAuth]
        }

        @http(method: "POST", uri: "/put-with-initial-members")
        operation PutWithInitialMembers {
            input := {
                room: String
                messages: Events
            }
        }

        @http(method: "POST", uri: "/put-stream-only")
        operation PutStreamOnly {
            input := {
                messages: Events
            }
        }

        @auth([])
        @http(method: "POST", uri: "/put-stream-only-without-auth")
        operation PutStreamOnlyWithoutAuth {
            input := {
                messages: Events
            }
        }

        @streaming
        union Events {
            message: Message
        }

        structure Message {
            message: String
        }
    """.toSmithyModel()

    private fun serializerFor(model: Model, operation: String): String {
        val ctx = model.newTestContext("CborExample")
        RpcV2Cbor().generateProtocolClient(ctx.generationCtx)
        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()
        return ctx.manifest.expectFileString("/src/main/kotlin/com/test/serde/${operation}OperationSerializer.kt")
    }

    private fun String.initialRequest(): String = lines("    val initialRequest = buildMessage {", "    }")

    @Test
    fun itAlwaysSendsAnInitialRequestMessageForRpcV2Cbor() {
        val serializer = serializerFor(model(sigv4 = false), "PutStreamOnly")

        // the caller may not provide any events at all, the initial-request message is still sent
        serializer.shouldContain("val stream = input.messages ?: emptyFlow()")
        serializer.shouldContain("mergeSequential(flowOf(initialRequest), stream.map(::encodePutStreamOnlyEventsEventMessage))")
        serializer.initialRequest().shouldContain("""addHeader(":event-type", HeaderValue.String("initial-request"))""")
    }

    @Test
    fun itSendsTheInitialRequestMessageWhenThereAreInitialRequestMembers() {
        val serializer = serializerFor(model(sigv4 = false), "PutWithInitialMembers")

        serializer.shouldContain("val stream = input.messages ?: emptyFlow()")
        serializer.initialRequest().shouldContain("""addHeader(":event-type", HeaderValue.String("initial-request"))""")
    }

    @Test
    fun itSetsTheInitialRequestMessageContentType() {
        val serializer = serializerFor(model(sigv4 = false), "PutStreamOnly")

        serializer.initialRequest().shouldContain("""addHeader(":content-type", HeaderValue.String("application/cbor"))""")
    }

    @Test
    fun itDoesNotSignMessagesWithoutSigV4Auth() {
        val serializer = serializerFor(model(sigv4 = false), "PutStreamOnly")

        serializer.shouldNotContain(".sign(context)")
        serializer.shouldNotContain("AwsSigningAttributes.Signer")
        serializer.shouldNotContain("AwsSigningAttributes.RequestSignature")
    }

    @Test
    fun itSignsMessagesWithSigV4Auth() {
        val serializer = serializerFor(model(sigv4 = true), "PutStreamOnly")

        serializer.shouldContain(".sign(context)")
        serializer.shouldContain("context[AwsSigningAttributes.Signer] = DefaultAwsSigner")
        serializer.shouldContain("context[AwsSigningAttributes.RequestSignature] = CompletableDeferred(context.coroutineContext.job)")
    }

    @Test
    fun itDoesNotSignMessagesForOperationsWithoutAuth() {
        val serializer = serializerFor(model(sigv4 = true), "PutStreamOnlyWithoutAuth")

        serializer.shouldNotContain(".sign(context)")
        serializer.shouldNotContain("AwsSigningAttributes.Signer")
        serializer.shouldNotContain("AwsSigningAttributes.RequestSignature")
    }

    @Test
    fun itAlwaysUsesAnEmptyBodyHashForTheInitialHttpRequest() {
        listOf(
            serializerFor(model(sigv4 = false), "PutStreamOnly"),
            serializerFor(model(sigv4 = true), "PutStreamOnly"),
            serializerFor(model(sigv4 = true), "PutStreamOnlyWithoutAuth"),
        ).forEach {
            it.shouldContain("context[AwsSigningAttributes.HashSpecification] = HashSpecification.EmptyBody")
        }
    }
}
