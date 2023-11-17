/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.operation

import aws.smithy.kotlin.runtime.client.endpoints.Endpoint
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import aws.smithy.kotlin.runtime.net.url.Url
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OperationEndpointTest {
    @Test
    fun testHostIsSet() = runTest {
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com"))
        val request = SdkHttpRequest(HttpRequestBuilder())
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTPS, actual.url.scheme)
        assertEquals("api.test.com", actual.headers["Host"])
    }

    @Test
    fun testHostWithPort() = runTest {
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com:8080"))
        val request = SdkHttpRequest(HttpRequestBuilder())
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTPS, actual.url.scheme)
        assertEquals(8080, actual.url.port)
    }

    @Test
    fun testHostWithBasePath() = runTest {
        val endpoint = Endpoint(uri = Url.parse("https://api.test.com:8080/foo/bar"))
        val request = SdkHttpRequest(HttpRequestBuilder().apply { url.path.decoded = "/operation" })
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTPS, actual.url.scheme)
        assertEquals(8080, actual.url.port)
        assertEquals("/foo/bar/operation", actual.url.path.toString())
    }

    @Test
    fun testHostPrefix() = runTest {
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com"))
        val context = ExecutionContext().apply {
            set(HttpOperationContext.HostPrefix, "prefix.")
        }

        val request = SdkHttpRequest(context, HttpRequestBuilder().apply { url.path.decoded = "/operation" })
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("prefix.api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTP, actual.url.scheme)
        assertEquals("/operation", actual.url.path.toString())
    }

    @Test
    fun testEndpointPathPrefixWithNonEmptyPath() = runTest {
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com/path/prefix/"))
        val request = SdkHttpRequest(HttpRequestBuilder().apply { url.path.decoded = "/operation" })
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTP, actual.url.scheme)
        assertEquals("/path/prefix/operation", actual.url.path.toString())
    }

    @Test
    fun testEndpointPathPrefixWithEmptyPath() = runTest {
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com/path/prefix"))
        val request = SdkHttpRequest(HttpRequestBuilder())
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTP, actual.url.scheme)
        assertEquals("/path/prefix", actual.url.path.toString())
    }

    @Test
    fun testQueryParameters() = runTest {
        val endpoint = Endpoint(uri = Url.parse("http://api.test.com?foo=bar&baz=qux"))
        val request = SdkHttpRequest(HttpRequestBuilder().apply { url.path.decoded = "/operation" })
        setResolvedEndpoint(request, endpoint)
        val actual = request.subject.build()

        assertEquals(Host.Domain("api.test.com"), actual.url.host)
        assertEquals(Scheme.HTTP, actual.url.scheme)
        assertEquals("/operation", actual.url.path.toString())
        assertEquals("bar", actual.url.parameters.decodedParameters["foo"]!!.single())
        assertEquals("qux", actual.url.parameters.decodedParameters["baz"]!!.single())
    }
}
