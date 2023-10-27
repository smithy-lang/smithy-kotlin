/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.io

import okio.Buffer

public actual class SdkBuffer : SdkBufferedSource, SdkBufferedSink {
    public actual val size: Long
        get() = TODO("Not yet implemented")

    public actual constructor()

    internal actual val inner: okio.Buffer
        get() = TODO("Not yet implemented")

    internal actual constructor(buffer: okio.Buffer)

    actual override val buffer: SdkBuffer
        get() = TODO("Not yet implemented")

    override fun write(source: ByteArray, offset: Int, limit: Int) {
        TODO("Not yet implemented")
    }

    override fun writeAll(source: SdkSource): Long {
        TODO("Not yet implemented")
    }

    override fun write(source: SdkSource, byteCount: Long) {
        TODO("Not yet implemented")
    }

    override fun writeUtf8(string: String, start: Int, endExclusive: Int) {
        TODO("Not yet implemented")
    }

    override fun writeByte(x: Byte) {
        TODO("Not yet implemented")
    }

    override fun writeShort(x: Short) {
        TODO("Not yet implemented")
    }

    override fun writeShortLe(x: Short) {
        TODO("Not yet implemented")
    }

    override fun writeInt(x: Int) {
        TODO("Not yet implemented")
    }

    override fun writeIntLe(x: Int) {
        TODO("Not yet implemented")
    }

    override fun writeLong(x: Long) {
        TODO("Not yet implemented")
    }

    override fun writeLongLe(x: Long) {
        TODO("Not yet implemented")
    }

    override fun flush() {
        TODO("Not yet implemented")
    }

    override fun emit() {
        TODO("Not yet implemented")
    }

    override fun skip(byteCount: Long) {
        TODO("Not yet implemented")
    }

    override fun readByte(): Byte {
        TODO("Not yet implemented")
    }

    override fun readShort(): Short {
        TODO("Not yet implemented")
    }

    override fun readShortLe(): Short {
        TODO("Not yet implemented")
    }

    override fun readLong(): Long {
        TODO("Not yet implemented")
    }

    override fun readLongLe(): Long {
        TODO("Not yet implemented")
    }

    override fun readInt(): Int {
        TODO("Not yet implemented")
    }

    override fun readIntLe(): Int {
        TODO("Not yet implemented")
    }

    override fun readAll(sink: SdkSink): Long {
        TODO("Not yet implemented")
    }

    override fun read(sink: ByteArray, offset: Int, limit: Int): Int {
        TODO("Not yet implemented")
    }

    override fun readByteArray(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readByteArray(byteCount: Long): ByteArray {
        TODO("Not yet implemented")
    }

    override fun readUtf8(): String {
        TODO("Not yet implemented")
    }

    override fun readUtf8(byteCount: Long): String {
        TODO("Not yet implemented")
    }

    override fun peek(): SdkBufferedSource {
        TODO("Not yet implemented")
    }

    override fun exhausted(): Boolean {
        TODO("Not yet implemented")
    }

    override fun request(byteCount: Long): Boolean {
        TODO("Not yet implemented")
    }

    override fun require(byteCount: Long) {
        TODO("Not yet implemented")
    }

    override fun write(source: SdkBuffer, byteCount: Long) {
        TODO("Not yet implemented")
    }

    override fun read(sink: SdkBuffer, limit: Long): Long {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
    }
}
