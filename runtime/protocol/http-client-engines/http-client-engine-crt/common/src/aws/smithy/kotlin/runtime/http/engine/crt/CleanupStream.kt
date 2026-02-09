/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HttpStream

/**
 * Platform-specific cleanup for HttpStream resources.
 * On JVM: Explicitly closes the stream to decrement CrtResource reference count.
 * On Native: No-op, as CRT automatically releases the stream.
 */
internal expect fun cleanupStream(stream: HttpStream)
