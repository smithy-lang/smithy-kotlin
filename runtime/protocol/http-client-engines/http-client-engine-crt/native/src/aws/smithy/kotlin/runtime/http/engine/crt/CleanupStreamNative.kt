/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.http.engine.crt

import aws.sdk.kotlin.crt.http.HttpStream

/**
 * Native implementation: No-op, as the CRT automatically releases the stream during onResponseComplete.
 * Calling close() would cause a double-free segfault.
 */
internal actual fun cleanupStream(stream: HttpStream) {
    // No-op: CRT manages stream lifecycle automatically
}
