package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.http.HttpBody
import aws.smithy.kotlin.runtime.http.interceptors.requestcompression.util.CompressionAlgorithm

internal class Gzip: CompressionAlgorithm {
    override val id: String = "gzip"

    override suspend fun compress(stream: HttpBody): HttpBody {
        return HttpBody.Empty
    }
}