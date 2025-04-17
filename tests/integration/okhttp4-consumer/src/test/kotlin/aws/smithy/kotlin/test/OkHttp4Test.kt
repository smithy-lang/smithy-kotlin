package aws.smithy.kotlin.test

import aws.smithy.kotlin.runtime.http.HttpMethod
import aws.smithy.kotlin.runtime.http.HttpStatusCode
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.engine.okhttp.OkHttpEngine
import aws.smithy.kotlin.runtime.http.readAll
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.url
import aws.smithy.kotlin.runtime.httptest.TestWithLocalServer
import aws.smithy.kotlin.runtime.net.url.Url
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private const val TEST_ENDPOINT = "/userAgent"
private const val EXPECTED_UA_PREFIX = "okhttp/4." // e.g., "okhttp/4.12.0"

class OkHttp4Test : TestWithLocalServer() {
    override val server = embeddedServer(CIO, serverPort) {
        routing {
            get(TEST_ENDPOINT) {
                call.respondText(call.request.headers["User-Agent"] ?: "no User-Agent header found")
            }
        }
    }

    @Test
    fun testOkHttp4() = runTest {
        OkHttpEngine().use { okHttp ->
            val req = HttpRequestBuilder().apply {
                method = HttpMethod.GET
                url(Url.parse("http://localhost:$serverPort$TEST_ENDPOINT"))
            }.build()

            val call = SdkHttpClient(okHttp).call(req)
            val resp = call.response

            assertEquals(HttpStatusCode.OK, resp.status)

            val body = resp.body.readAll()?.decodeToString()
            assertNotNull(body, "Expected a non-null response body")

            assertTrue(
                body.startsWith(EXPECTED_UA_PREFIX),
                "Expected User-Agent header to begin with \"$EXPECTED_UA_PREFIX\" but got \"$body\"",
            )
        }
    }
}
