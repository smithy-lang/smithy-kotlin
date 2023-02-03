/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen

/**
 * Trim the whitespace from around every line. Useful for multi-line string searches where leading/trailing whitespace
 * isn't important.
 */
fun String.trimEveryLine() = lines().joinToString("\n", transform = String::trim)
