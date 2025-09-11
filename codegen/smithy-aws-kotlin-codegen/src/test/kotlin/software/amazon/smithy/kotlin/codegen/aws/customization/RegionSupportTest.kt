/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.aws.customization

import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.ServiceClientConfigGenerator
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.ServiceShape

class RegionSupportTest {
    @Test
    fun testRegionSupportProperties() {
        val model = """
            namespace com.test

            use aws.protocols#awsJson1_1
            use aws.api#service
            use aws.auth#sigv4

            @service(sdkId: "service with overrides", endpointPrefix: "service-with-overrides")
            @sigv4(name: "example")
            @awsJson1_1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            operation GetFoo {}
        """.toSmithyModel()

        val serviceShape = model.expectShape<ServiceShape>("com.test#Example")

        val testCtx = model.newTestContext(serviceName = "Example")
        val writer = KotlinWriter("com.test")

        val renderingCtx = testCtx.toRenderingContext(writer, serviceShape)
            .copy(integrations = listOf(RegionSupport()))

        ServiceClientConfigGenerator(serviceShape, detectDefaultProps = false).render(renderingCtx, renderingCtx.writer)
        val contents = writer.toString()

        val expectedProps = """
            public val region: String? = builder.region
            public val regionProvider: RegionProvider? = builder.regionProvider
        """.formatForTest()
        contents.shouldContainOnlyOnceWithDiff(expectedProps)

        val expectedImpl = """
            /**
             * The AWS region to sign with and make requests to. When specified, this static region configuration
             * takes precedence over other region resolution methods.
             *
             * The region resolution order is:
             * 1. Static region (if specified)
             * 2. Custom region provider (if configured)
             * 3. Default region provider chain
             */
            public var region: String? = null
    
            /**
             * An optional region provider that determines the AWS region for client operations. When specified, this provider
             * takes precedence over the default region provider chain, unless a static region is explicitly configured.
             *
             * The region resolution order is:
             * 1. Static region (if specified)
             * 2. Custom region provider (if configured)
             * 3. Default region provider chain
             */
            public var regionProvider: RegionProvider? = null
        """.formatForTest(indent = "        ")
        contents.shouldContainOnlyOnceWithDiff(expectedImpl)
    }
}
