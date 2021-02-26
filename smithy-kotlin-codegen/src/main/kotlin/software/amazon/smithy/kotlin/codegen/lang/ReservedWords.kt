/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.codegen.core.ReservedWords
import software.amazon.smithy.codegen.core.ReservedWordsBuilder

/**
 * Get the Kotlin language reserved words
 */
fun kotlinReservedWords(): ReservedWords = ReservedWordsBuilder().apply {
    hardReservedWords.forEach { put(it, "`$it`") }
}.build()

val hardReservedWords = listOf(
    "as",
    "as?",
    "break",
    "class",
    "continue",
    "do",
    "else",
    "false",
    "for",
    "fun",
    "if",
    "in",
    "!in",
    "interface",
    "is",
    "!is",
    "null",
    "object",
    "package",
    "return",
    "super",
    "this",
    "throw",
    "true",
    "try",
    "typealias",
    "typeof",
    "val",
    "var",
    "when",
    "while"
)
