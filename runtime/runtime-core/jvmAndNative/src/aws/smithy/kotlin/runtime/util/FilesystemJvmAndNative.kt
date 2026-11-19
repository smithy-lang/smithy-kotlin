/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.util

import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import okio.buffer
import okio.use

internal actual fun readFileBytes(path: String, amount: Long, offset: Long, readAll: Boolean): ByteArray = FileSystem.SYSTEM.source(path.toPath()).buffer().use {
    if (readAll) {
        it.readByteArray()
    } else {
        it.skip(offset)
        it.readByteArray(amount)
    }
}
