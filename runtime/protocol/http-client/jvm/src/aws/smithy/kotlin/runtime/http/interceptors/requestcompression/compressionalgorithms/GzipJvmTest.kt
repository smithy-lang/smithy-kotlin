/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.interceptors.requestcompression.compressionalgorithms

import aws.smithy.kotlin.runtime.content.toByteArray
import aws.smithy.kotlin.runtime.http.toByteStream
import aws.smithy.kotlin.runtime.io.*
import java.util.zip.GZIPInputStream
import kotlin.text.Charsets.UTF_8

@Test
fun testGzipSdkSource() = runTest {

    val byteArray = ByteArray(19456) { 0xf }.source() // TODO: Remove size ... maybe use other constructor
    val gzipSdkSource = GzipSdkSource(byteArray)
    while (gzipSdkSource.read(SdkBuffer(), 1) != -1L) { } // TODO: Try changing limit ... look at how read to buffer does it "gzipByteReadChannel.readToBuffer().readByteArray()"
    val compressedByteArray = gzipSdkSource.readToByteArray()

    assertTrue(compressedByteArray, "THE COMPRESSED BYTE ARRAY")

    val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(UTF_8).use { it.readText() }
    assertTrue(decompressedByteArray, byteArray)
}

@Test
fun testGzipByteReadChannel() = runTest  {

    val byteArray = ByteArray(19456) { 0xf }.source().toSdkByteReadChannel() // TODO: Remove size ... maybe use other constructor
    val gzipByteReadChannel = GzipByteReadChannel(byteArray)
    val compressedByteArray = gzipByteReadChannel.readToBuffer().readByteArray()

    assertTrue(compressedByteArray, "THE COMPRESSED BYTE ARRAY")

    val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(UTF_8).use { it.readText() }
    assertTrue(decompressedByteArray, byteArray)
}

@Test
fun testBytes() = runTest {
    val byteArray = ByteArray(19456) { 0xf } // TODO: Remove size ... maybe use other constructor
    val compressedByteArray = compressByteArray(byteArray).toByteStream()!!.toByteArray()

    assertTrue(compressedByteArray, "THE COMPRESSED BYTE ARRAY")

    val decompressedByteArray = GZIPInputStream(compressedByteArray.inputStream()).bufferedReader(UTF_8).use { it.readText() }
    assertTrue(decompressedByteArray, byteArray)
}

