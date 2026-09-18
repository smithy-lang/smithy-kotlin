/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.longpoll

import aws.smithy.kotlin.codegen.test.MockHttpProtocolGenerator
import aws.smithy.kotlin.codegen.test.lines
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import kotlin.test.Test
import kotlin.test.assertFalse

class LongPollingIntegrationTest {

    private fun longPollModel(timeoutMillis: Int = 70000) =
        """
            namespace com.test
            use aws.protocols#awsJson1_1
            use aws.api#service
            use aws.auth#sigv4

            @service(sdkId: "test")
            @sigv4(name: "test")
            @awsJson1_1
            service Test {
                version: "1.0.0",
                operations: [LongPollOp]
            }

            @longPoll(timeoutMillis: $timeoutMillis)
            @http(method: "POST", uri: "/longpoll")
            operation LongPollOp {
                input: LongPollInput
                output: LongPollOutput
            }

            @input
            structure LongPollInput {}
            @output
            structure LongPollOutput {}
        """.toSmithyModel()

    private val normalModel =
        """
            namespace com.test
            use aws.protocols#awsJson1_1
            use aws.api#service
            use aws.auth#sigv4

            @service(sdkId: "test")
            @sigv4(name: "test")
            @awsJson1_1
            service Test {
                version: "1.0.0",
                operations: [NormalOp]
            }

            @http(method: "POST", uri: "/normal")
            operation NormalOp {
                input: NormalInput
                output: NormalOutput
            }

            @input
            structure NormalInput {}
            @output
            structure NormalOutput {}
        """.toSmithyModel()

    private fun generateClient(model: software.amazon.smithy.model.Model): String {
        val ctx = model.newTestContext(integrations = listOf(LongPollingIntegration()))
        val generator = MockHttpProtocolGenerator(model)
        generator.generateProtocolClient(ctx.generationCtx)
        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()
        return ctx.manifest.expectFileString("/src/main/kotlin/com/test/DefaultTestClient.kt")
    }

    @Test
    fun testLongPollOperationGeneratesCorrectCode() {
        val actual = generateClient(longPollModel(70000))
        val method = actual.lines("    override suspend fun longPollOp(input: LongPollOpRequest): LongPollOpResponse {", "    }")
        method.shouldContainOnlyOnceWithDiff("op.context[HttpOperationContext.LongPolling] = true")
        method.shouldContainOnlyOnceWithDiff("op.context[HttpOperationContext.SocketReadTimeout] = 70000.milliseconds")
    }

    @Test
    fun testNormalOperationUnaffected() {
        val actual = generateClient(normalModel)
        val method = actual.lines("    override suspend fun normalOp(input: NormalOpRequest): NormalOpResponse {", "    }")
        assertFalse(method.contains("LongPolling"), "Normal operation should not set LongPolling")
    }
}
