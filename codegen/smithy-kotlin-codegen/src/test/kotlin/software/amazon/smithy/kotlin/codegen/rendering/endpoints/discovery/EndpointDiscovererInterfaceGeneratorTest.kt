/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery

import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.test.formatForTest
import software.amazon.smithy.kotlin.codegen.test.newTestContext
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.toCodegenContext

class EndpointDiscovererInterfaceGeneratorTest {
    @Test
    fun testInterface() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                /**
                 * Represents the logic for automatically discovering endpoints for Test calls
                 */
                public interface TestEndpointDiscoverer {
                    public fun asEndpointResolver(client: TestClient, delegate: EndpointResolver): EndpointResolver
            """.trimIndent(),
        )

        actual.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun invalidate(context: ExecutionContext)
            """.trimIndent(),
        )

        actual.shouldContainOnlyOnceWithDiff(
            """
                }
                
                public data class DiscoveryParams(private val region: String?, private val identity: String)
                public val DiscoveryParamsKey: AttributeKey<DiscoveryParams> = AttributeKey("DiscoveryParams")
            """.trimIndent(),
        )
    }

    @Test
    fun testDiscoverHost() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                public suspend fun discoverHost(client: TestClient): ExpiringValue<Host> =
                    client.getEndpoints()
                        .endpoints
                        ?.map { ep -> ExpiringValue(
                            Host.parse(ep.address!!),
                            Instant.now() + ep.cachePeriodInMinutes.minutes,
                        )}
                        ?.firstOrNull()
                        ?: throw EndpointProviderException("Unable to discover any endpoints when invoking getEndpoints!")
            """.formatForTest(),
        )
    }

    private fun render(): String {
        val model = model()
        val testCtx = model.newTestContext()
        val delegator = testCtx.generationCtx.delegator
        val generator = EndpointDiscovererInterfaceGenerator(testCtx.toCodegenContext(), delegator)
        generator.render()

        delegator.flushWriters()
        val testManifest = delegator.fileManifest as MockManifest
        return testManifest.expectFileString("/src/main/kotlin/com/test/endpoints/TestEndpointDiscoverer.kt")
    }
}
