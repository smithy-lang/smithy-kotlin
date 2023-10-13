/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.utils

/**
 * Split a string on word boundaries
 */
fun String.splitOnWordBoundaries(): List<String> {
    // This is taken from Rust: https://github.com/awslabs/smithy-rs/pull/3037/files#diff-4175c66ee81a450fcf1cd3e256f36ae2c8e0b30b910be8ca505135fbe215144d
    // with minor changes (s3 and iot as whole words). Previously we used the Java v2 implementation
    // https://github.com/aws/aws-sdk-java-v2/blob/2.20.162/utils/src/main/java/software/amazon/awssdk/utils/internal/CodegenNamingUtils.java#L36
    // but this has some edge cases it doesn't handle well
    val out = mutableListOf<String>()
    // These are whole words but cased differently, e.g. `IPv4`, `MiB`, `GiB`, `TtL`
    val completeWords = listOf("ipv4", "ipv6", "sigv4", "mib", "gib", "kib", "ttl", "iot", "s3")
    var currentWord = ""

    // emit the current word and update from the next character
    val emit = { next: Char ->
        if (currentWord.isNotEmpty()) {
            out += currentWord.lowercase()
        }
        currentWord = if (next.isLetterOrDigit()) {
            next.toString()
        } else {
            ""
        }
    }

    val allLowerCase = lowercase() == this
    forEachIndexed { index, nextChar ->
        val peek = getOrNull(index + 1)
        val doublePeek = getOrNull(index + 2)
        val completeWordInProgress = completeWords.any {
            (currentWord + substring(index)).lowercase().startsWith(it)
        } && !completeWords.contains(currentWord.lowercase())

        when {
            // [C] in these docs indicates the value of nextCharacter
            // A[_]B
            !nextChar.isLetterOrDigit() -> emit(nextChar)

            // If we have no letters so far, push the next letter (we already know it's a letter or digit)
            currentWord.isEmpty() -> currentWord += nextChar.toString()

            // Abc[D]ef or Ab2[D]ef
            !completeWordInProgress && loweredFollowedByUpper(currentWord, nextChar) -> emit(nextChar)

            // s3[k]ey
            !completeWordInProgress && allLowerCase && digitFollowedByLower(currentWord, nextChar) -> emit(nextChar)

            // DB[P]roxy, or `IAM[U]ser` but not AC[L]s
            endOfAcronym(currentWord, nextChar, peek, doublePeek) -> emit(nextChar)

            // emit complete words
            !completeWordInProgress && completeWords.contains(currentWord.lowercase()) -> emit(nextChar)

            // If we haven't found a word boundary, push it and keep going
            else -> currentWord += nextChar.toString()
        }
    }
    if (currentWord.isNotEmpty()) {
        out += currentWord
    }

    return out
}

/**
 * Handle cases like `DB[P]roxy`, `ARN[S]upport`, `AC[L]s`
 */
private fun endOfAcronym(current: String, nextChar: Char, peek: Char?, doublePeek: Char?): Boolean {
    if (!current.last().isUpperCase()) {
        // Not an acronym in progress
        return false
    }
    if (!nextChar.isUpperCase()) {
        // We aren't at the next word yet
        return false
    }

    if (peek?.isLowerCase() != true) {
        return false
    }

    // Skip cases like `AR[N]s`, `AC[L]s` but not `IAM[U]ser`
    if (peek == 's' && (doublePeek == null || !doublePeek.isLowerCase())) {
        return false
    }

    // Skip cases like `DynamoD[B]v2`
    return !(peek == 'v' && doublePeek?.isDigit() == true)
}

private fun loweredFollowedByUpper(current: String, nextChar: Char): Boolean {
    if (!nextChar.isUpperCase()) {
        return false
    }
    return current.last().isLowerCase() || current.last().isDigit()
}

private fun loweredFollowedByDigit(current: String, nextChar: Char): Boolean {
    if (!nextChar.isDigit()) {
        return false
    }
    return current.last().isLowerCase()
}

private fun digitFollowedByLower(current: String, nextChar: Char): Boolean =
    (current.last().isDigit() && nextChar.isLowerCase())

/**
 * Convert a string to `PascalCase` (uppercase start with upper case word boundaries)
 */
fun String.toPascalCase(): String = splitOnWordBoundaries().joinToString(separator = "") { it.lowercase().replaceFirstChar { c -> c.uppercaseChar() } }

/**
 * Convert a string to `camelCase` (lowercase start with upper case word boundaries)
 */
fun String.toCamelCase(): String = toPascalCase().replaceFirstChar { c -> c.lowercaseChar() }

/**
 * Inverts the case of a character. For example:
 * * 'a' → 'A'
 * * 'A' → 'a'
 * * '!' → '!'
 */
fun Char.toggleCase(): Char = if (isUpperCase()) lowercaseChar() else uppercaseChar()

/**
 * Toggles the case of the first character in the string. For example:
 * * "apple" → "Apple"
 * * "Apple" → "apple"
 * * "!apple" → "!apple"
 */
fun String.toggleFirstCharacterCase(): String = when {
    isEmpty() -> this
    else -> first().toggleCase() + substring(1)
}
