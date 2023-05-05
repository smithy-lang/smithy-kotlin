package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngine
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineBase
import aws.smithy.kotlin.runtime.http.engine.HttpClientEngineConfig
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpCall
import aws.smithy.kotlin.runtime.http.response.HttpResponse
import aws.smithy.kotlin.runtime.operation.ExecutionContext
import aws.smithy.kotlin.runtime.time.Instant

public fun TestEngine(
    name: String = "test",
    roundTripImpl: (ExecutionContext, HttpRequest) -> HttpCall = { _, request ->
        val resp = HttpResponse(HttpStatusCode.OK, Headers.Empty, HttpBody.Empty)
        val now = Instant.now()
        HttpCall(request, resp, now, now)
    },
): HttpClientEngine =
    object : HttpClientEngineBase(name) {
        override val config: HttpClientEngineConfig = HttpClientEngineConfig.Default

        override suspend fun roundTrip(context: ExecutionContext, request: HttpRequest): HttpCall =
            roundTripImpl(context, request)
    }
