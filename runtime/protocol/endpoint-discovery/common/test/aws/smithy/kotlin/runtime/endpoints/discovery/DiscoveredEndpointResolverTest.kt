/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.endpoints.discovery

import aws.smithy.kotlin.runtime.auth.awscredentials.Credentials
import aws.smithy.kotlin.runtime.client.SdkClientOption
import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.client.operationName
import aws.smithy.kotlin.runtime.http.HeadersBuilder
import aws.smithy.kotlin.runtime.http.operation.EndpointResolver
import aws.smithy.kotlin.runtime.http.operation.ResolveEndpointRequest
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.net.*
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.ManualClock
import aws.smithy.kotlin.runtime.util.AttributeKey
import aws.smithy.kotlin.runtime.util.ExpiringValue
import aws.smithy.kotlin.runtime.util.attributesOf
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveredEndpointResolverTest {
    @Test
    fun testResolution() = runTest {
        var activeRegion = "mars"
        var activeIdentity = Credentials("alice", "secret")

        val clock = ManualClock()
        val resolver = DiscoveredEndpointResolver(delegateEndpointResolver, { activeRegion }, clock = clock) {
            listOf(
                ExpiringValue(
                    Host.parse("${activeIdentity.accessKeyId}.$activeRegion.amazonaws.com"),
                    clock.now() + 2.seconds,
                ),
            )
        }

        suspend fun resolve(op: String) = resolver.resolve(
            ResolveEndpointRequest(
                ExecutionContext.build { attributes[SdkClientOption.OperationName] = op },
                HttpRequest { },
                activeIdentity,
            ),
        )

        assertEndpoint(resolve("foo"), "alice.mars.amazonaws.com", "foo")
        assertEndpoint(resolve("bar"), "alice.mars.amazonaws.com", "bar")

        activeRegion = "mercury"
        assertEndpoint(resolve("bar"), "alice.mercury.amazonaws.com", "bar")

        activeIdentity = Credentials("bob", "secret")
        assertEndpoint(resolve("bar"), "bob.mercury.amazonaws.com", "bar")
    }
}

private val attributeKey = AttributeKey<String>("someAttr")

private val expectedPort = 123
private val expectedHeaders = HeadersBuilder().apply { set("abc", "def") }.build()
private val expectedAttributes = attributesOf { attributeKey to "attrValue" }

val delegateEndpointResolver = EndpointResolver {
    val op = it.context.operationName!!
    Endpoint(
        UrlBuilder {
            scheme = Scheme.HTTPS
            host = Host.parse("generic.amazonaws.com")
            port = expectedPort
            parameters {
                append("op", op)
            }
        },
        expectedHeaders,
        expectedAttributes,
    )
}

private fun assertEndpoint(actual: Endpoint, expectedHost: String, expectedOp: String) {
    assertEquals(Scheme.HTTPS, actual.uri.scheme)
    assertEquals(expectedHost, actual.uri.host.toString())
    assertEquals(expectedPort, actual.uri.port)
    assertEquals(expectedOp, actual.uri.parameters["op"])
    assertEquals(expectedHeaders, actual.headers)
    assertEquals(expectedAttributes, actual.attributes)
}
