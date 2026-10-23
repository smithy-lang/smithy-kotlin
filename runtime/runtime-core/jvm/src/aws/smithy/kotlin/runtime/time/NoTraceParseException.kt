/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.time

internal actual class NoTraceParseException actual constructor(
    input: String,
    message: String,
    position: Int,
) : ParseException(input, message, position) {
    override fun fillInStackTrace(): Throwable = this
}
