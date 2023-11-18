package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.SdkHttpClient
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.RequestCompressionTraitInterceptor
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms.Gzip
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.operation.newTestOperation
import aws.smithy.kotlin.runtime.http.operation.roundTrip
import aws.smithy.kotlin.runtime.http.request.HttpRequestBuilder
import aws.smithy.kotlin.runtime.http.request.headers
import aws.smithy.kotlin.runtime.httptest.TestEngine
import aws.smithy.kotlin.runtime.util.get
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestCompressionTraitInterceptorTest {
    private val client = SdkHttpClient(TestEngine())

    @Test
    fun testNoCompression() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                0,
                listOf(),
                listOf(),
            )
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toString()) // TODO: Might not be right really
    }

    @Test
    fun testInvalidCompressionThreshold() = runTest { // TODO: Do the asset fails with exception thing
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                -1,
                listOf("gzip"),
                listOf(Gzip()),
            )
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toString()) // TODO: Might not be right really
    }

    @Test
    fun testCompression() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                0,
                listOf("gzip"),
                listOf(Gzip()),
            )
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toString()) // TODO: Might not be right really
    }

    @Test
    fun testCompressionWithMultipleHeaders() = runTest {
        val req = HttpRequestBuilder().apply {
            body = HttpBody.fromBytes("<Foo>bar</Foo>".encodeToByteArray())
        }
        req.headers { append("Content-Encoding", "br") }

        val op = newTestOperation<Unit, Unit>(req, Unit)

        op.interceptors.add(
            RequestCompressionTraitInterceptor(
                0,
                listOf("gzip"),
                listOf(Gzip()),
            )
        )

        op.roundTrip(client, Unit)
        val call = op.context.attributes[HttpOperationContext.HttpCallList].first()
        assertEquals("gzip", call.request.headers["Content-Encoding"])
        assertEquals("<Foo>bar</Foo>", call.request.body.toString()) // TODO: Might not be right really
    }
}