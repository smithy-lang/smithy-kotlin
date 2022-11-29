/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.io.SdkBuffer

internal actual fun transferRequestBody(outgoing: SdkBuffer, dest: MutableBuffer) {
    outgoing.read(dest.buffer)
}
