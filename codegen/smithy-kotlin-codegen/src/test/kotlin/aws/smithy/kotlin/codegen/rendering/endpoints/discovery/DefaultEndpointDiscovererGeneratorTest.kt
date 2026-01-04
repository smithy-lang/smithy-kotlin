/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints.discovery

import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import aws.smithy.kotlin.codegen.test.formatForTest
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toCodegenContext
import software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery.DefaultEndpointDiscovererGenerator

class DefaultEndpointDiscovererGeneratorTest {
    private val renderedCodegen: String = run {
        val model = model()
        val testCtx = model.newTestContext()
        val delegator = testCtx.generationCtx.delegator
        val generator = DefaultEndpointDiscovererGenerator(testCtx.toCodegenContext(), delegator)
        generator.render()

        delegator.flushWriters()
        val testManifest = delegator.fileManifest as MockManifest
        testManifest.expectFileString("/src/main/kotlin/com/test/endpoints/DefaultTestEndpointDiscoverer.kt")
    }

    @Test
    fun testClass() {
        renderedCodegen.shouldContainOnlyOnceWithDiff(
            """
                /**
                 * A class which looks up specific endpoints for Test calls via the `getEndpoints` API. These
                 * unique endpoints are cached as appropriate to avoid unnecessary latency in subsequent calls.
                 * @param cache An [ExpiringKeyedCache] implementation used to cache discovered hosts
                 */
                public class DefaultTestEndpointDiscoverer(public val cache: ExpiringKeyedCache<DiscoveryParams, Host> = PeriodicSweepCache(10.minutes)) : TestEndpointDiscoverer {
            """.trimIndent(),
        )
    }

    @Test
    fun testAsEndpointResolver() {
        renderedCodegen.shouldContainOnlyOnceWithDiff(
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
        renderedCodegen.shouldContainOnlyOnceWithDiff(
            """
                override public suspend fun invalidate(context: ExecutionContext) {
                    context.getOrNull(DiscoveryParamsKey)?.let { cache.invalidate(it) }
                }
            """.formatForTest(),
        )
    }
}
