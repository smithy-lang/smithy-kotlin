/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HttpStream

/**
 * JVM implementation: Explicitly close the stream to decrement CrtResource reference count.
 * This follows aws-crt-java's documented pattern and ensures timely cleanup without relying on GC.
 */
internal actual fun cleanupStream(stream: HttpStream) {
    stream.close()
}
