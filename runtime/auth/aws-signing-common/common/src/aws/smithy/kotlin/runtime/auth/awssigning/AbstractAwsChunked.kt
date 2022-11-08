package aws.smithy.kotlin.runtime.auth.awssigning

import aws.smithy.kotlin.runtime.io.SdkByteReadChannel

internal abstract class AbstractAwsChunked(
    private val chan: SdkByteReadChannel,
    private val signer: AwsSigner,
    private val signingConfig: AwsSigningConfig,
    private var previousSignature: ByteArray
): SdkByteReadChannel by chan {
    companion object {
        const val CHUNK_SIZE_BYTES: Int = 65536
    }

    var chunk: ByteArray? = null
    var chunkOffset: Int = 0

    /**
     * Returns all the bytes remaining in the underlying data source, up to [limit].
     * @return a [ByteArray] containing at most [limit] bytes. it may contain fewer if there are less than [limit] bytes
     * remaining in the data source.
     */
    override suspend fun readRemaining(limit: Int): ByteArray {
        if (chunk == null || chunkOffset >= chunk!!.size) { chunk = getNextChunk() }

        var bytesWritten = 0
        var bytes = byteArrayOf()
        while (bytesWritten != limit) {
            val numBytesToWrite: Int = minOf(limit - bytesWritten, chunk!!.size - chunkOffset)

            bytes += chunk!!.slice(chunkOffset .. chunkOffset + numBytesToWrite)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            // read a new chunk. this handles the case where we consumed the whole chunk but still have not sent `limit` bytes
            if (chunk == null || chunkOffset >= chunk!!.size) { chunk = getNextChunk() }
        }

        return bytes
    }

    /**
     * Writes [length] bytes to [sink], starting [offset] bytes from the beginning. If [length] bytes are not available in
     * the source data, the call will fail with an [IllegalArgumentException].
     *
     * @param sink the destination [ByteArray] to write to
     * @param offset the number of bytes in [sink] to skip before beginning to write
     * @param length the number of bytes to write to [sink]
     * @throws IllegalArgumentException when illegal [offset] and [length] arguments are passed
     * @throws RuntimeException when the source data is exhausted before [length] bytes are written to [sink]
     */
    override suspend fun readFully(sink: ByteArray, offset: Int, length: Int) {
        require(!chan.isClosedForRead) { "Invalid read: channel is closed for reading" }
        require(offset >= 0) { "Invalid read: offset must be positive:  $offset" }
        require(offset + length <= sink.size) { "Invalid read: offset + length should be less than the destination size: $offset + $length < ${sink.size}" }

        // make sure the chunk is valid
        if (chunk == null || chunkOffset >= chunk!!.size) { chunk = getNextChunk() }

        var bytesWritten = 0
        while (bytesWritten != length) {
            val numBytesToWrite: Int = minOf(length, chunk!!.size - chunkOffset)

            val bytes = chunk!!.slice(chunkOffset  .. chunkOffset + numBytesToWrite).toByteArray()

            bytes.copyInto(sink, offset + bytesWritten)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            if (chunk == null || chunkOffset >= chunk!!.size) {
                chunk = getNextChunk()

                // we may get nothing after reading next chunk -- this means the underlying source has closed, and we should fail
                if(chunk == null || chunk!!.isEmpty()) {
                    throw RuntimeException("Invalid read: unable to fully read $length bytes. missing $length - $bytesWritten bytes.")
                }
            }
        }
    }

    /**
     * Writes [length] bytes to [sink], starting [offset] bytes from the beginning.
     * Returns when [length] bytes or the number of available bytes have been written, whichever is lower.
     * @param sink the bytearray to write the data to
     * @param offset the number of bytes to skip from the beginning of the chunk
     * @param length the maximum number of bytes to write to [sink]. the actual number of bytes written may be fewer if
     * there are less immediately available.
     * @throws IllegalArgumentException when illegal [offset] and [length] arguments are passed
     * @return an [Int] representing the number of bytes written
     */
    override suspend fun readAvailable(sink: ByteArray, offset: Int, length: Int): Int {
        // input validation
        require(!chan.isClosedForRead) { "Invalid read: channel is closed for reading" }
        require(offset >= 0) { "Invalid read: offset must be positive:  $offset" }
        require(offset + length <= sink.size) { "Invalid read: offset + length should be less than the destination size: $offset + $length < ${sink.size}" }

        // make sure the current chunk is valid -- suspend and read a new chunk if not valid
        if (chunk == null || chunkOffset >= chunk!!.size) { chunk = getNextChunk() }

        var bytesWritten = 0
        while (bytesWritten != length) {
            val numBytesToWrite = minOf(length, chunk!!.size - chunkOffset)

            val bytes = chunk!!.slice(chunkOffset .. chunkOffset + numBytesToWrite).toByteArray()

            bytes.copyInto(sink, offset + bytesWritten)

            bytesWritten += numBytesToWrite
            chunkOffset += numBytesToWrite

            // if we've exhausted the current chunk, exit without suspending for a new one
            if (chunk == null || chunkOffset >= chunk!!.size) { break }
        }

        return bytesWritten
    }

    /**
     * Read the next chunk of data and add hex-formatted chunk size and chunk signature to the front
     * This function assumes that the previous chunk has been fully consumed and there is no remaining data, because the
     * previous chunk will be overwritten with new data.
     * @return an aws-chunked encoded ByteArray with the hex-formatted chunk size, chunk signature, and chunk data
     * (in that order), ready to send on the wire
     */
    suspend fun getNextChunk(): ByteArray? {
        val chunkBody = chan.readRemaining(CHUNK_SIZE_BYTES)

        if (chunkBody.isEmpty()) { return null }

        val chunkSignature = signer.signChunk(chunkBody, previousSignature, signingConfig).signature
        previousSignature = chunkSignature

        // the chunk header consists of the size of the chunk encoded in hexadecimal, followed by the chunk signature,
        // separated with a semicolon
        val chunkHeader = buildString {
            append(chunkBody.size.toString(16))
            append(";")
            append("chunk-signature=")
            appendLine(chunkSignature)
        }.encodeToByteArray()

        chunkOffset = 0
        return chunkHeader + chunkBody
    }

}