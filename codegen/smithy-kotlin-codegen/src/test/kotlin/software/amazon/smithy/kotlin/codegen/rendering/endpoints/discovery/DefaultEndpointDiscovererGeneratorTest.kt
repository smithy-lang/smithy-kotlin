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

class DefaultEndpointDiscovererGeneratorTest {
    @Test
    fun testClass() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                /**
                 * A class which looks up specific endpoints for Test calls via the `getEndpoints`
                 * API. These unique endpoints are cached as appropriate to avoid unnecessary latency in subsequent
                 * calls.
                 * @param cache An [ExpiringKeyedCache] implementation used to cache discovered hosts
                 */
                public class DefaultTestEndpointDiscoverer(public val cache: ExpiringKeyedCache<DiscoveryParams, Host> = PeriodicSweepCache(10.minutes, Clock.System)) : TestEndpointDiscoverer {
            """.trimIndent(),
        )
    }

    @Test
    fun testAsEndpointResolver() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                override fun asEndpointResolver(client: TestClient, delegate: EndpointResolver): EndpointResolver = EndpointResolver { request ->
                    if (client.config.endpointUrl == null) {
                        val identity = request.identity
                        require(identity is Credentials) { "Endpoint discovery requires AWS credentials" }
                
                        val cacheKey = DiscoveryParams(client.config.region, identity.accessKeyId)
                        request.context[DiscoveryParamsKey] = cacheKey
                        val discoveredHost = cache.get(cacheKey) { discoverHost(client) }
                
                        val originalEndpoint = delegate.resolve(request)
                        Endpoint(
                            originalEndpoint.uri.copy { host = discoveredHost },
                            originalEndpoint.headers,
                            originalEndpoint.attributes,
                        )
                    } else {
                        delegate.resolve(request)
                    }
                }
            """.formatForTest(),
        )
    }

    @Test
    fun testInvalidate() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                override public suspend fun invalidate(context: ExecutionContext) {
                    context.getOrNull(DiscoveryParamsKey)?.let { cache.invalidate(it) }
                }
            """.formatForTest(),
        )
    }

    private fun render(): String {
        val model = model()
        val testCtx = model.newTestContext()
        val delegator = testCtx.generationCtx.delegator
        val generator = DefaultEndpointDiscovererGenerator(testCtx.toCodegenContext(), delegator)
        generator.render()

        delegator.flushWriters()
        val testManifest = delegator.fileManifest as MockManifest
        return testManifest.expectFileString("/src/main/kotlin/com/test/endpoints/DefaultTestEndpointDiscoverer.kt")
    }
}
