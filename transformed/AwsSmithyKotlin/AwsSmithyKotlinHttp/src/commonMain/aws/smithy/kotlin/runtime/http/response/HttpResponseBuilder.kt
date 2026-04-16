/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.http.response

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.http.*
import aws.smithy.kotlin.runtime.util.CanDeepCopy

/**
 * Used to construct an HTTP response
 * @param status The HTTP status of the response
 * @param headers Response HTTP headers
 * @param body Response payload
 */
@InternalApi
public class HttpResponseBuilder private constructor(
    public var status: HttpStatusCode,
    public val headers: HeadersBuilder,
    public var body: HttpBody,
) : CanDeepCopy<HttpResponseBuilder> {
    public constructor() : this(HttpStatusCode.OK, HeadersBuilder(), HttpBody.Empty)

    public fun build(): HttpResponse = HttpResponse(status, if (headers.isEmpty()) Headers.Empty else headers.build(), body)

    override fun deepCopy(): HttpResponseBuilder = HttpResponseBuilder(status, headers.deepCopy(), body)

    override fun toString(): String = "HttpResponseBuilder(status=$status, headers=$headers, body=$body)"
}

internal data class HttpResponseBuilderView(
    internal val builder: HttpResponseBuilder,
    internal val allowToBuilder: Boolean,
) : HttpResponse {
    override val status: HttpStatusCode = builder.status
    override val headers: Headers by lazy { builder.headers.build() }
    override val body: HttpBody = builder.body
    override val summary: String = "HTTP ${status.value} ${status.description}"
}

/**
 * Create a read-only view of a builder. Often, we need a read-only view of a builder that _may_ get modified.
 * This would normally require a round trip invoking [HttpResponseBuilder.build] and then converting that back
 * to a builder using [HttpResponse.toBuilder]. Instead, we can create an immutable view of a builder that
 * is cheap to convert to a builder.
 *
 * @param allowToBuilder flag controlling how this type will behave when [HttpResponse.toBuilder] is invoked. When
 * false an exception will be thrown, otherwise it will succeed.
 */
@InternalApi
public fun HttpResponseBuilder.immutableView(
    allowToBuilder: Boolean = false,
): HttpResponse = HttpResponseBuilderView(this, allowToBuilder)
