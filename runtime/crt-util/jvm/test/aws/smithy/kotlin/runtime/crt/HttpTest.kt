/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.net.Host
import aws.smithy.kotlin.runtime.net.Scheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import aws.sdk.kotlin.crt.http.Headers as HeadersCrt
import aws.sdk.kotlin.crt.http.HttpRequest as HttpRequestCrt

class HttpTest {
    @Test
    fun testRequestBuilderUpdate() {
        // test updating HttpRequestBuilder from a (signed) crt request

        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url {
                scheme = Scheme.HTTPS
                host = Host.Domain("test.com")
                port = 3000
                path.encoded = "/foo/bar/baz"
                parameters.decodedParameters.add("foo", "bar")
            }

            headers {
                append("k1", "v1")
                append("k2", "v3")
            }
        }

        // build a slightly modified crt request (e.g. after signing new headers or query params will be present)
        val crtHeaders = HeadersCrt.build {
            append("k1", "v1")
            append("k1", "v2")
            append("k2", "v3")
            append("k3", "v4")
        }
        val crtRequest = HttpRequestCrt("POST", "/foo/bar/baz?foo=bar&baz=quux", crtHeaders, null)

        builder.update(crtRequest)

        // crt request doesn't have all the same elements (e.g. host/scheme) since some of them live off
        // HttpConnectionManager for instance
        // ensure we don't overwrite the originals
        assertEquals(Host.Domain("test.com"), builder.url.host)
        assertEquals(Scheme.HTTPS, builder.url.scheme)

        // see that the crt headers are populated in the builder
        crtHeaders.entries().forEach { entry ->
            entry.value.forEach { value ->
                assertTrue(builder.headers.contains(entry.key, value), "expected header pair: ${entry.key}: $value")
            }
        }

        assertEquals("/foo/bar/baz", builder.url.path.encoded)

        assertTrue(builder.url.parameters.decodedParameters.contains("foo", "bar"))
        assertTrue(builder.url.parameters.decodedParameters.contains("baz", "quux"))
    }

    @Test
    fun testRequestBuilderUpdateNoQuery() {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url {
                scheme = Scheme.HTTPS
                host = Host.Domain("test.com")
                path.encoded = "/foo"
            }
        }

        // build a slightly modified crt request (e.g. after signing new headers or query params will be present)
        val crtHeaders = HeadersCrt.build { append("k1", "v1") }
        val crtRequest = HttpRequestCrt("POST", "/foo", crtHeaders, null)

        builder.update(crtRequest)

        // crt request doesn't have all the same elements (e.g. host/scheme) since some of them live off
        // HttpConnectionManager for instance
        // ensure we don't overwrite the originals
        assertEquals(Host.Domain("test.com"), builder.url.host)
        assertEquals(Scheme.HTTPS, builder.url.scheme)

        assertEquals("/foo", builder.url.path.encoded)
    }

    @Test
    fun testEncodedPath() {
        // test updating HttpRequestBuilder from a (signed) crt request with a percent-encoded path

        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.POST
            url {
                scheme = Scheme.HTTPS
                host = Host.Domain("test.com")
                port = 3000
                path.encoded = "/foo/bar/baz"
                parameters.decodedParameters.add("foo", "/")
            }
        }

        // build a slightly modified crt request (e.g. after signing new headers or query params will be present)
        val crtHeaders = HeadersCrt.build { }
        val crtRequest = HttpRequestCrt("POST", builder.url.path.encoded, crtHeaders, null)

        builder.update(crtRequest)

        assertEquals("/foo/bar/baz", builder.url.path.encoded)

        val values = builder.url.parameters.decodedParameters.getValue("foo")
        assertEquals(1, values.size)
        assertEquals("/", values.first())
    }
}
