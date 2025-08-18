package aws.smithy.kotlin.runtime.auth.awssigning.internal

import aws.smithy.kotlin.runtime.auth.awssigning.AwsSignatureType
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigner
import aws.smithy.kotlin.runtime.auth.awssigning.AwsSigningConfig
import aws.smithy.kotlin.runtime.auth.awssigning.HashSpecification
import aws.smithy.kotlin.runtime.http.DeferredHeaders
import aws.smithy.kotlin.runtime.http.toHeaders
import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.io.SdkSink
import kotlin.math.min

internal class StreamChunker(
    private val input: Adapter,
    private val signer: AwsSigner,
    private val signingConfig: AwsSigningConfig,
    private var previousSignature: ByteArray,
    private val trailingHeaders: DeferredHeaders,
    private val chunkSize: Long = CHUNK_SIZE_BYTES.toLong(),
) {
    interface Adapter {
        val eof: Boolean
        suspend fun read(buffer: SdkBuffer, limit: Long): Long
    }

    private val inputBuffer = SdkBuffer()
    private var hasLastChunkBeenSent = false

    internal suspend inline fun readAndMaybeWrite(destination: SdkSink, limit: Long): Long {
        if (input.eof) {
            if (hasLastChunkBeenSent) {
                return -1 // this stream is exhausted
            } else {
                // Write any remaining data as final chunk(s)
                if (inputBuffer.size > 0) {
                    consumeAndWriteChunk(destination)
                }

                // Write an empty chunk, including any previous signature calculated if applicable
                wrapAndWriteChunk(byteArrayOf(), destination)

                // Write the tailer chunk if applicable
                if (!trailingHeaders.isEmpty()) writeTrailerChunk(destination)

                // Always end with blank line?
                SdkBuffer().apply { writeUtf8("\r\n") }.readAll(destination)

                hasLastChunkBeenSent = true
                return 0 // no bytes read from source
            }
        } else {
            val rc = input.read(inputBuffer, limit)
            while (inputBuffer.size >= chunkSize) {
                consumeAndWriteChunk(destination)
            }
            if (rc == -1L) {
                check(input.eof) { "Got back -1 bytes but input adapter is not EOF" }
                return 0
            }
            return rc
        }
    }

    private suspend fun consumeAndWriteChunk(destination: SdkSink) {
        val chunkBody = inputBuffer.readByteArray(min(chunkSize, inputBuffer.size))
        wrapAndWriteChunk(chunkBody, destination)
    }

    private suspend fun wrapAndWriteChunk(body: ByteArray, destination: SdkSink) {
        SdkBuffer()
            .apply {
                writeUtf8(body.size.toString(16))

                if (signingConfig.isSigned) {
                    writeUtf8(";chunk-signature=")

                    val signature = signer.signChunk(body, previousSignature, signingConfig).signature
                    write(signature) // signature is UTF-8-encoded hex string
                    previousSignature = signature
                }

                writeUtf8("\r\n")
                write(body)
                if (body.isNotEmpty()) writeUtf8("\r\n")
            }
            .readAll(destination)
    }

    private suspend fun writeTrailerChunk(destination: SdkSink) {
        val trailingHeaders = this.trailingHeaders.toHeaders()
        val buffer = SdkBuffer()
        buffer.writeTrailers(trailingHeaders)

        if (signingConfig.isSigned) {
            val trailerSigningConfig = signingConfig.toTrailingHeadersSigningConfig()
            val trailerSignature = signer.signChunkTrailer(trailingHeaders, previousSignature, trailerSigningConfig).signature
            buffer.writeTrailerSignature(trailerSignature.decodeToString())
        }

        buffer.readAll(destination)
    }
}

private val AwsSigningConfig.isSigned: Boolean
    get() = hashSpecification != HashSpecification.StreamingUnsignedPayloadWithTrailers

private fun AwsSigningConfig.toTrailingHeadersSigningConfig(): AwsSigningConfig = this.toBuilder().apply {
    signatureType = AwsSignatureType.HTTP_REQUEST_TRAILING_HEADERS // signature is for trailing headers
    hashSpecification = HashSpecification.CalculateFromPayload // calculate the hash from the trailing headers payload
}.build()
