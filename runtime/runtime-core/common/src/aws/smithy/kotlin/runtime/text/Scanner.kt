/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.text

import aws.smithy.kotlin.runtime.InternalApi

/**
 * A stateful scanner for processing a string in pieces (e.g., during parsing). This class operates on a single input
 * [text] and tracks a current position (starting at the beginning). Various operations move or may move the position
 * forward but never backwards.
 * @param text The string to process
 */
@InternalApi
public class Scanner(public val text: String) {
    private var currentIndex = 0

    private fun findAnyOf(literals: Array<out String>): Pair<String, Int>? = literals
        .map { it to text.indexOf(it, startIndex = currentIndex) }
        .filter { (_, index) -> index != -1 }
        .minByOrNull { (_, index) -> index }

    /**
     * If [text] starts with [prefix] at the current position, advances the current position by the length of [prefix]
     * and invokes the given [handler]. Otherwise, the position does not change and the [handler] is not invoked.
     *
     * **Example**:
     *
     * ```kotlin
     * val scanner = Scanner("abc def")
     * scanner.ifStartsWith("abc") {
     *     // Scanner has now skipped "abc"
     *     println(scanner) // Prints "Scanner(' def')"
     * }
     * ```
     *
     * @param prefix The prefix to test for at the current position
     * @param handler The handler to invoke if [text] starts with [prefix] at the current position
     */
    public fun ifStartsWith(prefix: String, handler: () -> Unit) {
        if (startsWith(prefix)) {
            currentIndex += prefix.length
            handler()
        }
    }

    /**
     * If any of [literals] is found at/after the current position, invokes the given [handler] on the substring *up to
     * but not including* the literal and then advances the current position *past* the literal. If multiple of the
     * given [literals] are found, the nearest one is processed. If none of [literals] are found, the position does not
     * change and the [handler] is not invoked.
     *
     * This method is similar to [requireAndSkip] except that no exception is thrown when none of [literals] are found.
     *
     * **Example**:
     *
     * ```kotlin
     * val scanner = Scanner("ianbotsf@somewhere.net")
     * scanner.optionalAndSkip("@") { username ->
     *     println(username) // Prints "ianbotsf"
     * }
     * // Scanner has now skipped "ianbotsf@"
     * println(scanner) // Prints "Scanner('somewhere.net')"
     * ```
     *
     * @param literals One or more strings to search for at/after the current position
     * @param handler The handler to invoke on the substring *up to but not including* the nearest found element of
     * [literals]. If none of [literals] are found, this handler is not invoked.
     */
    public fun optionalAndSkip(vararg literals: String, handler: (String) -> Unit) {
        findAnyOf(literals)?.let { (literal, startIndex) ->
            processAndSkip(literal, startIndex, handler)
        }
    }

    private fun process(untilIndex: Int, handler: (String) -> Unit) {
        val captured = text.substring(currentIndex, untilIndex)
        currentIndex = untilIndex
        handler(captured)
    }

    private fun processAndSkip(literal: String, startIndex: Int, handler: (String) -> Unit) {
        process(startIndex, handler)
        currentIndex += literal.length
    }

    /**
     * If any of [literals] is found at/after the current position, invokes the given [handler] on the substring *up to
     * but not including* the literal and then advances the current position *past* the literal. If multiple of the
     * given [literals] are found, the nearest one is processed. If none of [literals] is found, an
     * [IllegalArgumentException] is thrown.
     *
     * This method is similar to [optionalAndSkip] except that an exception is thrown when none of [literals] are found.
     *
     * **Example**:
     *
     * ```kotlin
     * val scanner = Scanner("ianbotsf@somewhere.net")
     * scanner.requireAndSkip("@") { username ->
     *     println(username) // Prints "ianbotsf"
     * }
     * // Scanner has now skipped "ianbotsf@"
     * println(scanner) // Prints "Scanner('somewhere.net')"
     * ```
     *
     * @param literals One or more strings to search for at/after the current position
     * @param handler The handler to invoke on the substring *up to but not including* the nearest found element of
     * [literals].
     */
    public fun requireAndSkip(vararg literals: String, handler: (String) -> Unit) {
        val (literal, startIndex) = requireNotNull(findAnyOf(literals)) { "Cannot find any of ${literals.toList()}" }
        processAndSkip(literal, startIndex, handler)
    }

    override fun toString(): String = "Scanner(remainingText='${text.substring(currentIndex)}')"

    /**
     * Determines if [text] starts with [prefix] at the current position.
     * @param prefix The prefix to search for at the current position
     * @return True if [text] at the current position starts with [prefix]; otherwise, false
     */
    public fun startsWith(prefix: String): Boolean = text.regionMatches(currentIndex, prefix, 0, prefix.length)

    /**
     * Invokes the given [handler] on the substring *up to but not including* the nearest found element of [literals]
     * at/after the current position. If none of [literals] are found, invokes [handler] on the remainder of [text] from
     * the current position to the end of the string. After [handler] is invoked, the current position is advanced past
     * the found element of [literals] or past the end of the string if none of [literals] are found.
     *
     * **Example**:
     *
     * ```kotlin
     * val scanner = Scanner("abc,def")
     * scanner.upToOrEnd(",") {
     *     println(it) // Prints "abc"
     * }
     * // Scanner has now skipped "abc,"
     * scanner.upToOrEnd(",") {
     *     println(it) // Prints "def"
     * }
     * // Scanner has now skipped "def"
     * ```
     *
     * @param literals One or more strings to search for at/after the current position
     * @param handler The handler to invoke on the substring *up to but not including nearest found element of
     * [literals] or, if none of [literals] are found,
     */
    public fun upToOrEnd(vararg literals: String, handler: (String) -> Unit) {
        val untilIndex = findAnyOf(literals)?.second ?: text.length
        process(untilIndex, handler)
    }
}
