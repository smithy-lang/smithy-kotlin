package aws.smithy.kotlin.runtime.http.interceptors

import aws.smithy.kotlin.runtime.businessmetrics.emitBusinessMetric
import aws.smithy.kotlin.runtime.client.ProtocolRequestInterceptorContext
import aws.smithy.kotlin.runtime.hashing.HashFunction
import aws.smithy.kotlin.runtime.hashing.resolveChecksumAlgorithmHeaderName
import aws.smithy.kotlin.runtime.hashing.toBusinessMetric
import aws.smithy.kotlin.runtime.http.operation.HttpOperationContext
import aws.smithy.kotlin.runtime.http.request.HttpRequest
import aws.smithy.kotlin.runtime.http.request.toBuilder
import aws.smithy.kotlin.runtime.http.toCompletingBody
import aws.smithy.kotlin.runtime.http.toHashingBody
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job

/**
 * Configures [HttpRequest] with AWS chunked streaming to calculate checksum during transmission
 * @return [HttpRequest]
 */
internal fun calculateAwsChunkedStreamingChecksum(
    context: ProtocolRequestInterceptorContext<Any, HttpRequest>,
    checksumAlgorithm: HashFunction,
): HttpRequest {
    val request = context.protocolRequest.toBuilder()
    val deferredChecksum = CompletableDeferred<String>(context.executionContext.coroutineContext.job)
    val checksumHeader = checksumAlgorithm.resolveChecksumAlgorithmHeaderName()

    request.body = request.body
        .toHashingBody(checksumAlgorithm, request.body.contentLength)
        .toCompletingBody(deferredChecksum)

    request.headers.append("x-amz-trailer", checksumHeader)
    request.trailingHeaders.append(checksumHeader, deferredChecksum)

    context.executionContext.emitBusinessMetric(checksumAlgorithm.toBusinessMetric())

    return request.build()
}

/**
 * @return The default checksum algorithm name in the execution context, null if default checksums are disabled.
 */
internal val ProtocolRequestInterceptorContext<Any, HttpRequest>.defaultChecksumAlgorithmName: String?
    get() = executionContext.getOrNull(HttpOperationContext.DefaultChecksumAlgorithm)
