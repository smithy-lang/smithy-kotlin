/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.io

/**
 * A synchronous read only stream of bytes (similar to java.io.InputStream)
 */
public interface Source : Closeable {

    fun read(sink: Buffer, byteCount: Int): Int

//    fun cursor(): Cursor?
}
