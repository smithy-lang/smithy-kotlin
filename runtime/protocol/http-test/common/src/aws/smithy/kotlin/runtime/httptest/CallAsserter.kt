package aws.smithy.kotlin.runtime.httptest

import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts equality between two [HttpRequest] instances. Implementations of this interface are free to choose criteria
 * for the equality assertion.
 */
public interface CallAsserter {
    /**
     * Verify that [expected] and [actual] are equal according to this asserter's criteria. If not, an [AssertionError]
     * is thrown.
     * @param msgPrefix The prefix to include in the message if an [AssertionError] is thrown
     * @param expected The expected request
     * @param actual The actual request
     */
    public suspend fun assertEquals(msgPrefix: String, expected: HttpRequest, actual: HttpRequest)

    public companion object {
        /**
         * Asserter that verifies every part of the requests match
         */
        public val FullyMatching: CallAsserter = List(
            MatchingMethods,
            MatchingUrls,
            MatchingHeaders.All,
            MatchingBodies,
        )
    }

    /**
     * Asserter that delegates to a collection of sub-asserters
     */
    public class List(private vararg val asserters: CallAsserter) : CallAsserter {
        override suspend fun assertEquals(msgPrefix: String, expected: HttpRequest, actual: HttpRequest) {
            asserters.forEach { it.assertEquals(msgPrefix, expected, actual) }
        }
    }

    /**
     * Asserter that verifies the methods of the requests match
     */
    public object MatchingMethods : CallAsserter {
        override suspend fun assertEquals(msgPrefix: String, expected: HttpRequest, actual: HttpRequest) {
            assertEquals(expected.method, actual.method, "$msgPrefix: Method mismatch")
        }
    }

    /**
     * Asserter that verifies the URLs of the requests match
     */
    public object MatchingUrls : CallAsserter {
        override suspend fun assertEquals(msgPrefix: String, expected: HttpRequest, actual: HttpRequest) {
            assertEquals(expected.url.toString(), actual.url.toString(), "$msgPrefix: URL mismatch")
        }
    }

    /**
     * Asserter that verifies headers of the requests match
     * @param shouldVerifyHeader A predicate which indicates whether a header with the given key should be verified
     */
    public class MatchingHeaders(private val shouldVerifyHeader: (String) -> Boolean) : CallAsserter {
        override suspend fun assertEquals(msgPrefix: String, expected: HttpRequest, actual: HttpRequest) {
            expected.headers.forEach { name, values ->
                if (shouldVerifyHeader(name)) {
                    values.forEach {
                        assertTrue(actual.headers.contains(name, it), "$msgPrefix: header `$name` missing value `$it`")
                    }
                }
            }
        }
        
        public companion object {
            /**
             * Asserter that verifies every header of the requests match
             */
            public val All: MatchingHeaders = MatchingHeaders { true }
        }
    }

    /**
     * Asserter that verifies the bodies of the requests match
     */
    public object MatchingBodies : CallAsserter {
        override suspend fun assertEquals(msgPrefix: String, expected: HttpRequest, actual: HttpRequest) {
            val expectedBody = expected.body.readAll()?.decodeToString()
            val actualBody = actual.body.readAll()?.decodeToString()
            assertEquals(expectedBody, actualBody, "$msgPrefix: body mismatch")
        }
    }
}
