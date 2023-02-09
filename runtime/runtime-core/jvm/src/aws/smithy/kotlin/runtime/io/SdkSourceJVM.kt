/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.io

import aws.smithy.kotlin.runtime.InternalApi
import aws.smithy.kotlin.runtime.io.internal.JobChannel
import kotlinx.coroutines.*

@InternalApi
public actual suspend fun SdkSource.readToByteArray(): ByteArray = withContext(Dispatchers.IO) {
    use { it.buffer().readByteArray() }
}

@InternalApi
@OptIn(DelicateCoroutinesApi::class)
public actual fun SdkSource.toSdkByteReadChannel(coroutineScope: CoroutineScope?): SdkByteReadChannel {
    val source = this
    val ch = JobChannel()
    val scope = coroutineScope ?: GlobalScope
    val job = scope.launch(Dispatchers.IO + CoroutineName("sdk-source-reader")) {
        val buffer = SdkBuffer()
        val result = runCatching {
            source.use {
                while (true) {
                    ensureActive()
                    val rc = source.read(buffer, DEFAULT_BYTE_CHANNEL_MAX_BUFFER_SIZE.toLong())
                    if (rc == -1L) break
                    ch.write(buffer)
                }
            }
        }

        ch.close(result.exceptionOrNull())
    }

    ch.attachJob(job)

    return ch
}
