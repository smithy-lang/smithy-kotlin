/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering.endpoints.discovery

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.ServiceClientConfigGenerator
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
        val serviceShape = model.expectShape<ServiceShape>("com.test#Test")

        val testCtx = model.newTestContext()
        val writer = KotlinWriter("com.test")

        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)
            .copy(integrations = listOf(EndpointDiscoveryIntegration()))

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, renderingCtx.writer)
        val contents = writer.toString()

        if (discoveryRequired) {
            val configStr = "public val endpointDiscoverer: TestEndpointDiscoverer = builder.endpointDiscoverer ?: TestEndpointDiscoverer()"
            contents.shouldContainOnlyOnceWithDiff(configStr)

            val builderStr = """
                /**
                 * The endpoint discoverer for this client
                 */
                public var endpointDiscoverer: TestEndpointDiscoverer? = null
            """.formatForTest("        ")
            contents.shouldContainOnlyOnceWithDiff(builderStr)
        } else {
            val configStr = "public val endpointDiscoverer: TestEndpointDiscoverer? = builder.endpointDiscoverer"
            contents.shouldContainOnlyOnceWithDiff(configStr)

            val builderStr = """
                /**
                 * The endpoint discoverer for this client, if applicable. By default, no endpoint
                 * discovery is provided. To use endpoint discovery, set this to a valid
                 * [TestEndpointDiscoverer] instance.
                 */
                public var endpointDiscoverer: TestEndpointDiscoverer? = null
            """.formatForTest("        ")
            contents.shouldContainOnlyOnceWithDiff(builderStr)
        }
    }

    private fun model(discoveryRequired: Boolean = true) =
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
