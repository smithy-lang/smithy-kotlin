/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.time

/**
 * A lightweight [ParseException] that suppresses stack trace capture on JVM.
 * Used internally by parser combinators (e.g., [alt]) where exceptions are used for control flow
 * and the stack trace is never inspected.
 */
internal expect class NoTraceParseException(input: String, message: String, position: Int) : ParseException
