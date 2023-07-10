/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.registerSectionWriter
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.ServiceClientConfigGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpTraitResolver
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.ServiceShape

class EndpointDiscoveryIntegrationTest {
    @Test
    fun testServiceClientPropertiesWhenRequired() {
        testServiceClientProperties(true)
    }

    @Test
    fun testServiceClientPropertiesWhenNotRequired() {
        testServiceClientProperties(false)
    }

    private fun testServiceClientProperties(discoveryRequired: Boolean) {
        val model = model(discoveryRequired)
        val serviceShape = model.expectShape<ServiceShape>("com.test#Example")

        val testCtx = model.newTestContext(serviceName = "Example")
        val writer = KotlinWriter("com.test")

        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)
            .copy(integrations = listOf(EndpointDiscoveryIntegration()))

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, renderingCtx.writer)
        val contents = writer.toString()

        val configStr = "public val useEndpointDiscovery: Boolean = builder.useEndpointDiscovery ?: false"
        val builderStr = """
            /**
             * Whether to use automatic endpoint discovery for operations where it is optional.
             */
            public var useEndpointDiscovery: Boolean? = null
        """.trimIndent().formatForTest("        ")
        if (discoveryRequired) {
            contents.shouldNotContainWithDiff(configStr)
            contents.shouldNotContainWithDiff(builderStr)
        } else {
            contents.shouldContainOnlyOnceWithDiff(configStr)
            contents.shouldContainOnlyOnceWithDiff(builderStr)
        }
    }

    @Test
    fun testDiscoveredEndpointResolver() {
        val model = model()
        val ctx = model.newTestContext(serviceName = "Example")
        val generator = TestProtocolClientGenerator(
            ctx.generationCtx,
            listOf(),
            HttpTraitResolver(ctx.generationCtx, "application/json"),
        )

        val writer = KotlinWriter(TestModelDefault.NAMESPACE)
        EndpointDiscoveryIntegration().sectionWriters.forEach {
            writer.registerSectionWriter(it.sectionId, it.emitter)
        }

        generator.render(writer)
        val contents = writer.toString()

        val expected = """
            private val discoveredEndpointResolver = DiscoveredEndpointResolver(EndpointResolverAdapter(config), config::region) {
                getEndpoints()
                    .endpoints
                    ?.map { ep -> ExpiringValue(
                        Host.parse(ep.address!!),
                        Instant.now() + ep.cachePeriodInMinutes.minutes
                    )}
                    ?: listOf()
            }
        """.trimIndent().formatForTest()
        contents.shouldContainOnlyOnceWithDiff(expected)

        contents.shouldContainOnlyOnceWithDiff("execution.endpointResolver = discoveredEndpointResolver")
    }

    private fun model(discoveryRequired: Boolean = true) =
        """
            namespace com.test

            use aws.protocols#awsJson1_1
            use aws.api#service
            use aws.auth#sigv4

            @service(sdkId: "example")
            @sigv4(name: "example")
            @awsJson1_1
            @aws.api#clientEndpointDiscovery(
                operation: GetEndpoints,
                error: BadEndpointError
            )
            service Example {
                version: "1.0.0",
                operations: [GetEndpoints, GetFoo]
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
            
            @aws.api#clientDiscoveredEndpoint(required: $discoveryRequired)
            @http(method: "GET", uri: "/foo")
            operation GetFoo {
                errors: [ BadEndpointError ]
            }
        """.toSmithyModel()
}
