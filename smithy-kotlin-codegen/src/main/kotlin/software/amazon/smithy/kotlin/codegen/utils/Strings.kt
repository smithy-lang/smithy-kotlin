/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.utils

fun String.doubleQuote(): String = "\"${this.slashEscape('\\').slashEscape('"')}\""
fun String.slashEscape(char: Char) = this.replace(char.toString(), """\$char""")

/**
 * Double quote a string, eg. "abc" -> "\"abc\""
 */
fun String.dq(): String = this.doubleQuote()
