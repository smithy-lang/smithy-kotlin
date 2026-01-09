/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.codegen.rendering.endpoints.discovery

import aws.smithy.kotlin.codegen.test.toSmithyModel

fun model() =
    // language=smithy
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
