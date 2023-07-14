/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery

import org.junit.jupiter.api.Test
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.kotlin.codegen.test.*

class EndpointDiscovererGeneratorTest {
    @Test
    fun testClass() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
            public class TestEndpointDiscoverer {
                private val cache = ReadThroughCache<DiscoveryParams, Host>(10.minutes, Clock.System)
            """.trimIndent(),
        )

        actual.shouldContainOnlyOnceWithDiff(
            """
                }
                
                private val discoveryParamsKey = AttributeKey<DiscoveryParams>("DiscoveryParams")
                private data class DiscoveryParams(private val region: String, private val identity: String)
            """.trimIndent(),
        )
    }

    @Test
    fun testAsEndpointResolver() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                internal suspend fun asEndpointResolver(client: TestClient, delegate: EndpointResolverAdapter) = EndpointResolver { request ->
                    val identity = request.identity
                    require(identity is Credentials) { "Endpoint discovery requires AWS credentials" }
            
                    val cacheKey = DiscoveryParams(client.config.region, identity.accessKeyId)
                    request.context[discoveryParamsKey] = cacheKey
                    val discoveredHost = cache.get(cacheKey) { discoverHost(client) }
            
                    val originalEndpoint = delegate.resolve(request)
                    Endpoint(
                        originalEndpoint.uri.copy(host = discoveredHost),
                        originalEndpoint.headers,
                        originalEndpoint.attributes,
                    )
                }
            """.formatForTest(),
        )
    }

    @Test
    fun testDiscoverHost() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                private suspend fun discoverHost(client: TestClient): ExpiringValue<Host> =
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

    @Test
    fun testInvalidate() {
        val actual = render()

        actual.shouldContainOnlyOnceWithDiff(
            """
                internal suspend fun invalidate(context: ExecutionContext) {
                    context.getOrNull(discoveryParamsKey)?.let { cache.invalidate(it) }
                }
            """.formatForTest(),
        )
    }

    private fun render(): String {
        val model = model()
        val testCtx = model.newTestContext()
        val delegator = testCtx.generationCtx.delegator
        val generator = EndpointDiscovererGenerator(testCtx.toCodegenContext(), delegator)
        generator.render()

        delegator.flushWriters()
        val testManifest = delegator.fileManifest as MockManifest
        return testManifest.expectFileString("/src/main/kotlin/com/test/endpoints/TestEndpointDiscoverer.kt")
    }

    private fun model() =
        """
            namespace com.test

            use aws.protocols#awsJson1_1
            use aws.api#service
            use aws.auth#sigv4

            @service(sdkId: "test")
            @sigv4(name: "test")
            @awsJson1_1
            @aws.api#clientEndpointDiscovery(
                operation: GetEndpoints,
                error: BadEndpointError
            )
            service Test {
                version: "1.0.0",
                operations: [GetEndpoints]
            }
            
            @error("client")
            @httpError(421)
            structure BadEndpointError { }

            @http(method: "GET", uri: "/endpoints")
            operation GetEndpoints {
                input: GetEndpointsInput
                output: GetEndpointsOutput
            }
            
            @input
            structure GetEndpointsInput { }
            
            @output
            structure GetEndpointsOutput {
                Endpoints: Endpoints
            }
            
            list Endpoints {
                member: Endpoint
            }
            
            structure Endpoint {
                Address: String
                CachePeriodInMinutes: Long
            }
        """.toSmithyModel()
}
