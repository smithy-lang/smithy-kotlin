package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.client.ProtocolResponseInterceptorContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.response.HttpResponse

/**
 * Interceptor for smoke test runner clients.
 *
 * A passing test is not predicated on an SDK being able to parse the server response received, it’s based on the
 * response’s HTTP status code.
 */
@InternalApi
public class SmokeTestsInterceptor : HttpInterceptor {
    override fun readBeforeDeserialization(context: ProtocolResponseInterceptorContext<Any, HttpRequest, HttpResponse>) {
        val status = context.protocolResponse.status.value
        when (status) {
            in 400..599 -> throw SmokeTestsFailureException("Smoke test failed with HTTP status code: $status")
            in 200..299 -> throw SmokeTestsSuccessException("Smoke test succeeded with HTTP status code: $status")
            else -> throw SmokeTestsUnexpectedException("Smoke test returned HTTP status code: $status")
        }
    }
}

@InternalApi public class SmokeTestsFailureException(message: String) : Exception(message)

@InternalApi public class SmokeTestsSuccessException(message: String) : Exception(message)
private class SmokeTestsUnexpectedException(message: String) : Exception(message)
