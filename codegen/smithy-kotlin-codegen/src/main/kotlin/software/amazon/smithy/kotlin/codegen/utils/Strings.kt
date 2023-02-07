/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.utils

import software.amazon.smithy.utils.StringUtils

fun String.doubleQuote(): String = StringUtils.escapeJavaString(this, "")

/**
 * Double quote a string, eg. "abc" -> "\"abc\""
 */
fun String.dq(): String = this.doubleQuote()

/**
 * Convert a namespace formatted string to a path, eg. "a.b.c" -> "a/b/c"
 */
fun String.namespaceToPath() = replace('.', '/')
