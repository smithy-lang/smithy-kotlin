package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.io.*
import kotlinx.coroutines.test.runTest
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class GzipJvmTest {
    @Test
    fun testGzipSdkSource() = runTest {

        val byteArray = ByteArray(19456) { 0xf }
        val byteArraySource = byteArray.source() // TODO: Remove size ... maybe use other constructor
        val gzipSdkSource = GzipSdkSource(byteArraySource)
        while (gzipSdkSource.read(SdkBuffer(), 1) != -1L) { } // TODO: Try changing limit ... look at how read to buffer does it "gzipByteReadChannel.readToBuffer().readByteArray()"
        val compressedByteArray = gzipSdkSource.readToByteArray()

        assertEquals(compressedByteArray, ByteArray(19456) { 0xf })

        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray, byteArray.toString())
    }

    @Test
    fun testGzipByteReadChannel() = runTest  {

        val byteArray = ByteArray(19456) { 0xf }
        val byteArrayByteReadChannel = byteArray.source().toSdkByteReadChannel() // TODO: Remove size ... maybe use other constructor
        val gzipByteReadChannel = GzipByteReadChannel(byteArrayByteReadChannel)
        val compressedByteArray = gzipByteReadChannel.readToBuffer().readByteArray()

        assertEquals(compressedByteArray, ByteArray(19456) { 0xf })

        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray, byteArray.toString())
    }

    @Test
    fun testBytes() = runTest {
        val byteArray = "Hello World".encodeToByteArray() // TODO: Remove size ... maybe use other constructor
        debugPrinting(byteArray)
        val compressedByteArray = compressByteArray(byteArray).toByteStream()!!.toByteArray()
        debugPrinting(compressedByteArray)
        assertEquals(compressedByteArray, )

        val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        assertEquals(decompressedByteArray.encodeToByteArray(), byteArray)
    }
}

fun debugPrinting(x: Any) {
    println("\n\n\n\n\n$x\n\n\n\n\n")
}