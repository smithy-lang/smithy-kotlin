/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.core

import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.integration.SectionKey
import software.amazon.smithy.utils.AbstractCodeWriter
import software.amazon.smithy.utils.SimpleCodeWriter
import java.util.function.BiFunction

/**
 * Extension function that is more idiomatic Kotlin that is roughly the same purpose as
 * the provided function `openBlock(String textBeforeNewline, String textAfterNewline, Runnable r)`
 *
 * Example:
 * ```
 * writer.withBlock("{", "}") {
 *     write("foo")
 * }
 * ```
 *
 * Equivalent to:
 * ```
 * writer.openBlock("{")
 * writer.write("foo")
 * writer.closeBlock("}")
 * ```
 */
fun <T : AbstractCodeWriter<T>> T.withBlock(
    textBeforeNewLine: String,
    textAfterNewLine: String,
    vararg args: Any,
    block: T.() -> Unit,
): T = wrapBlockIf(true, textBeforeNewLine, textAfterNewLine, *args) { block(this) }

/**
 * Like [withBlock], except the closing write of [textAfterNewLine] does NOT include a line break.
 */
fun <T : AbstractCodeWriter<T>> T.withInlineBlock(
    textBeforeNewLine: String,
    textAfterNewLine: String,
    vararg args: Any,
    block: T.() -> Unit,
): T {
    openBlock(textBeforeNewLine, *args)
    block(this)
    closeInlineBlock(textAfterNewLine)
    return this
}

/**
 * Extension function that is more idiomatic Kotlin that is roughly the same purpose as an if block wrapped around
 * the provided function `openBlock(String textBeforeNewline, String textAfterNewline, Runnable r)`
 *
 * Example:
 * ```
 * writer.wrapBlockIf(foo == bar, "{", "}") {
 *     write("foo")
 * }
 * ```
 *
 * Equivalent to:
 * ```
 * if (foo == bar) writer.openBlock("{")
 * writer.write("foo")
 * if (foo == bar) writer.closeBlock("}")
 * ```
 */
fun <T : AbstractCodeWriter<T>> T.wrapBlockIf(
    condition: Boolean,
    textBeforeNewLine: String,
    textAfterNewLine: String,
    vararg args: Any,
    block: T.() -> Unit,
): T {
    if (condition) openBlock(textBeforeNewLine, *args)
    block(this)
    if (condition) closeBlock(textAfterNewLine)
    return this
}

/**
 * Like [AbstractCodeWriter.closeBlock], except the closing write of [textAfterNewLine] does NOT include a line break.
 */
fun <T : AbstractCodeWriter<T>> T.closeInlineBlock(textAfterNewLine: String): T {
    writeInline("\n")
    dedent()
    writeInline(textAfterNewLine)
    return this
}

/**
 * Extension function that closes the previous block, dedents, opens a new block with [textBeforeNewLine], and indents.
 *
 * This is useful for chaining if-if-else-else branches.
 *
 * Example:
 * ```
 * writer.openBlock("if (foo) {")
 *     .write("foo()")
 *     .closeAndOpenBlock("} else {")
 *     .write("bar()")
 *     .closeBlock("}")
 * ```
 */
fun <T : AbstractCodeWriter<T>> T.closeAndOpenBlock(
    textBeforeNewLine: String,
    vararg args: Any,
): T = apply {
    dedent()
    openBlock(textBeforeNewLine, *args)
}

/**
 * Declares a section for extension in codegen.  The [SectionId] should be specified as a child
 * of the type housing the codegen associated with the section. This keeps [SectionId]s closely
 * associated with their targets.
 */
fun <T : AbstractCodeWriter<T>> T.declareSection(
    id: SectionId,
    context: Map<SectionKey<*>, Any?> = emptyMap(),
    block: T.() -> Unit = { },
): T {
    val strmap = context.mapKeys { it.key.name }
    putContext(strmap)
    pushState(id.javaClass.canonicalName)
    block(this)
    popState()
    removeContext(strmap)
    return this
}

private fun <T : AbstractCodeWriter<T>> T.removeContext(context: Map<String, Any?>): Unit =
    context.keys.forEach { key -> removeContext(key) }

/**
 * Convenience function to get a typed value out of the context or throw if the key doesn't exist
 * or the type is wrong
 */
inline fun <W : AbstractCodeWriter<W>, reified V> AbstractCodeWriter<W>.getContextValue(key: String): V =
    checkNotNull(getContext(key) as? V) {
        "Expected `$key` in CodeWriter context"
    }

/**
 * Convenience function to get a typed value out of the context or throw if the key doesn't exist
 * or the type is wrong
 */
inline fun <W : AbstractCodeWriter<W>, reified V> AbstractCodeWriter<W>.getContextValue(key: SectionKey<V>): V = getContextValue(key.name)

/**
 * Convenience function to set a typed value in the context
 * @param key
 */
inline fun <W : AbstractCodeWriter<W>, reified V> AbstractCodeWriter<W>.putContextValue(
    key: SectionKey<V>,
    value: V,
): W = putContext(key.name, value)

/**
 * Convenience function to set context only if there is no value already associated with the given [key]
 */
fun <W : AbstractCodeWriter<W>> AbstractCodeWriter<W>.putMissingContext(key: String, value: Any) {
    if (getContext(key) != null) return
    putContext(key, value)
}

typealias InlineCodeWriter = AbstractCodeWriter<*>.() -> Unit

/**
 * Formatter to enable passing a writing function
 * @param codeWriterCreator function that creates a new [AbstractCodeWriter] instance used to generate output of inline
 * content
 */
class InlineCodeWriterFormatter(
    private val codeWriterCreator: () -> AbstractCodeWriter<*> = ::SimpleCodeWriter,
) : BiFunction<Any, String, String> {
    @Suppress("UNCHECKED_CAST")
    override fun apply(t: Any, u: String): String {
        val func = t as? InlineCodeWriter ?: error("Invalid parameter type of ${t::class}")
        val innerWriter = codeWriterCreator()
        func(innerWriter)
        return innerWriter.toString().trimEnd()
    }
}

/**
 * Optionally call the [Runnable] if [test] is true, otherwise do nothing and return the instance without
 * running the block
 */
fun <T : AbstractCodeWriter<T>> T.callIf(test: Boolean, runnable: Runnable): T {
    if (test) {
        runnable.run()
    }
    return this
}

/** Escape the [expressionStart] character to avoid problems during formatting */
fun <T : AbstractCodeWriter<T>> T.escape(text: String): String =
    text.replace("$expressionStart", "$expressionStart$expressionStart")
