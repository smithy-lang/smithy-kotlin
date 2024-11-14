/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.runtime.crt

import aws.sdk.kotlin.crt.io.MutableBuffer
import aws.smithy.kotlin.runtime.io.SdkBuffer

/**
 * write as much of [outgoing] to [dest] as possible
 */
internal expect fun transferRequestBody(outgoing: SdkBuffer, dest: MutableBuffer): Int
