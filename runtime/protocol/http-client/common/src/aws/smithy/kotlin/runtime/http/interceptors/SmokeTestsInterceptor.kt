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
            in 400..599 -> throw SmokeTestsFailureException()
            in 200..299 -> throw SmokeTestsSuccessException()
            else -> throw SmokeTestsUnexpectedException()
        }
    }
}

@InternalApi public class SmokeTestsFailureException : Exception()

@InternalApi public class SmokeTestsSuccessException : Exception()
private class SmokeTestsUnexpectedException : Exception()
