package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HttpStream
import aws.smithy.kotlin.runtime.http.toHttpBody
import aws.smithy.kotlin.runtime.io.SdkByteReadChannel
import aws.smithy.kotlin.runtime.io.readToByteArray
import aws.smithy.kotlin.runtime.io.source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.*


@OptIn(ExperimentalCoroutinesApi::class)
class SendChunkedBodyTest {
    private class MockHttpStream(override val responseStatusCode: Int) : HttpStream {
        var closed: Boolean = false
        var numChunksWritten = 0
        override fun activate() {}
        override fun close() { closed = true }
        override fun incrementWindow(size: Int) {}
        override fun writeChunk(chunkData: ByteArray, isFinalChunk: Boolean) { numChunksWritten += 1 }
    }

    @Test
    fun testSourceContent() = runTest {
        val stream = MockHttpStream(200)

        val chunkedBytes = """
           100;chunk-signature=${"0".repeat(64)}\r\n${"0".repeat(256)}\r\n\r\n 
        """.trimIndent().toByteArray()

        val source = chunkedBytes.source()

        stream.sendChunkedBody(source.toHttpBody(chunkedBytes.size.toLong()))

        // source should be fully consumed with 1 chunk written
        assertEquals(0, source.readToByteArray().size)
        assertEquals(1, stream.numChunksWritten)
    }

    @Test
    fun testChannelContentMultipleChunks() = runTest {
        val stream = MockHttpStream(200)

        val chunkSize = (CHUNK_BUFFER_SIZE * 5).toInt()

        val chunkedBytes = """
           ${chunkSize.toString(16)};chunk-signature=${"0".repeat(64)}\r\n${"0".repeat(chunkSize)}\r\n\r\n 
        """.trimIndent().toByteArray()

        val source = chunkedBytes.source()

        stream.sendChunkedBody(source.toHttpBody(chunkedBytes.size.toLong()))

        // source should be fully consumed
        assertEquals(0, source.readToByteArray().size)

        // there should definitely be more than 1 call to `writeChunk`, but in practice we don't care how many there are
        assertTrue(stream.numChunksWritten > 1)
    }

    @Test
    fun testChannelContent() = runTest {
        val stream = MockHttpStream(200)

        val chunkedBytes = """
           100;chunk-signature=${"0".repeat(64)}\r\n${"0".repeat(256)}\r\n\r\n 
        """.trimIndent().toByteArray()

        val channel = SdkByteReadChannel(chunkedBytes)

        stream.sendChunkedBody(channel.toHttpBody(chunkedBytes.size.toLong()))

        // channel should be fully consumed with 1 chunk written
        assertEquals(0, channel.availableForRead)
        assertTrue(channel.isClosedForRead)
        assertEquals(1, stream.numChunksWritten)
    }

    @Test
    fun testSourceContentMultipleChunks() = runTest {
        val stream = MockHttpStream(200)

        val chunkSize = (CHUNK_BUFFER_SIZE * 5).toInt()

        val chunkedBytes = """
           ${chunkSize.toString(16)};chunk-signature=${"0".repeat(64)}\r\n${"0".repeat(chunkSize)}\r\n\r\n 
        """.trimIndent().toByteArray()

        val channel = SdkByteReadChannel(chunkedBytes)

        stream.sendChunkedBody(channel.toHttpBody(chunkedBytes.size.toLong()))

        // source should be fully consumed
        assertEquals(0, channel.availableForRead)
        assertTrue(channel.isClosedForRead)

        // there should definitely be more than 1 call to `writeChunk`, but in practice we don't care how many there are
        assertTrue(stream.numChunksWritten > 1)
    }


}