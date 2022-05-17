/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeVisitor
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.transform.ModelTransformer

/**
 * Sanitize all instances of [DocumentationTrait] and converts them to KDoc-compliant strings.
 */
class DocumentationPreprocessor : KotlinIntegration {

    override fun preprocessModel(model: Model, settings: KotlinSettings): Model {
        val transformer = ModelTransformer.create()
        return transformer.mapTraits(model) { _, trait ->
            when (trait) {
                is DocumentationTrait -> {
                    // There's definitely some improperly escaped HTML characters within preformat blocks in existing
                    // models. Ensure we strip those now, the parser is VERY forgiving and will mistreat any sequences
                    // of characters that happen to form tags as such.
                    val sanitizedDoc = trait.value
                        .applyWithin("<code>", "</code>", String::escapeHtml)
                        .applyWithin("<pre>", "</pre>", String::escapeHtml)
                    val docs = toKdoc(sanitizedDoc)
                    DocumentationTrait(docs, trait.sourceLocation)
                }
                else -> trait
            }
        }
    }

    private fun toKdoc(doc: String): String {
        val parsed = parseClean(doc)

        val renderer = MarkdownRenderer()
        parsed.body().traverse(renderer)
        return renderer.text()
    }

    private fun parseClean(rawDoc: String): Document {
        val parsed = Jsoup.parse(rawDoc)

        parsed.body().stripBlankTextNodes()

        return parsed
    }

    private class MarkdownRenderer : NodeVisitor {
        companion object {
            const val SUBLIST_INDENT = "   "
            const val PREFORMAT_MARKER = "`"
            const val BOLD_MARKER = "**"
            const val ITALIC_MARKER = "*"
        }

        private var builder: StringBuilder = StringBuilder()

        private var bufferedAnchorHref: String = ""
        private var bufferedAnchorText: String = ""
        private var listPrefix: String = ""

        fun text() = builder.toString().trim()

        override fun head(node: Node, depth: Int) {
            if (node is TextNode) {
                if (node.parentNode()?.nodeName() == "a") {
                    bufferedAnchorText = node.markdownText()
                } else {
                    builder.append(node.markdownText())
                }
                return
            }

            when (node.nodeName()) {
                "a" -> {
                    if (node.hasAttr(("href"))) {
                        bufferedAnchorHref = node.attr("href")
                    }
                }
                "li" -> {
                    // If this list item holds a sublist, then we essentially just want to line break right away and
                    // render the nested list as normal.
                    val prefix = if (node.childNode(0).nodeName() == "ul") "\n" else ""
                    builder.append("$listPrefix+ $prefix")
                }
                "ul", "ol" -> {
                    if (node.hasAncestor(Node::isList)) {
                        sublistIndent()
                    }
                }
                "code", "pre" -> builder.append(PREFORMAT_MARKER)
                "b", "strong" -> builder.append(BOLD_MARKER)
                "i", "em" -> builder.append(ITALIC_MARKER)
                "br" -> builder.ensureLineBreak()

                // Definition lists (dl, dt, dd) have a corresponding md syntax, but neither intellij nor dokka will
                // render them. Treat definition terms as a "header" and let the descriptions flow out like
                // normal markdown content.
                "dt" -> builder.append("## ")

                // Anecdotally this appears to be used to render a "title" display - it always appears at the start
                // of documents.
                "fullname" -> builder.append("# ")

                "body", "p", "note", "important", "dd", "dl", "div" -> {
                    // Known elements that we can ignore here - they have no bearing on the output.
                }

                // Occasionally there will be unescaped angle brackets within elements. Those tags will sometimes
                // join to form "elements" that will trick the rather forgiving Jsoup parser, eg.
                // "<p>specify the URI in the form 's3://<bucket_name>/</p>"
                // The safest approach to malformed input like this is just to write out the content as-is, such
                // that no information is destroyed. The worst outcome is that some seemingly nonsense HTML tag is
                // injected into the output, which a reader can reasonably ignore.
                else -> builder.append("<${node.nodeName()}>")
            }
        }

        override fun tail(node: Node, depth: Int) {
            when (node.nodeName()) {
                "p", "div", "dd" -> {
                    val nextSibling = node.nextSibling()
                    when {
                        // break to give the upcoming list a new line
                        nextSibling != null && nextSibling.isList() -> builder.ensureLineBreak()
                        // if we're inside a list, the outer list item will close out the line for us
                        node.hasAncestor(Node::isList) -> return
                        // all other cases: this is a standalone "text block" which should be displayed as its own
                        // paragraph
                        else -> builder.ensureSectionBreak()
                    }
                }
                "a" -> writeBufferedAnchor()
                "ul", "ol" -> {
                    sublistDedent()
                    if (node.parent()?.nodeName() == "body") {
                        builder.ensureSectionBreak()
                    }
                }
                "code", "pre" -> builder.append(PREFORMAT_MARKER)
                "b", "strong" -> builder.append(BOLD_MARKER)
                "i", "em" -> builder.append(ITALIC_MARKER)
                "li", "fullname", "dt" -> builder.ensureLineBreak()
            }
        }

        private fun writeBufferedAnchor() {
            // Model docs will sometimes contain an anchor without the href. At that point there's no real way of
            // knowing to what it refers, nor can we guarantee a valid link just by bracketing it.
            builder.append(
                if (bufferedAnchorHref != "") {
                    "[$bufferedAnchorText]($bufferedAnchorHref)"
                } else {
                    bufferedAnchorText
                }
            )

            bufferedAnchorHref = ""
            bufferedAnchorText = ""
        }

        private fun sublistIndent() {
            listPrefix += SUBLIST_INDENT
        }

        private fun sublistDedent() {
            listPrefix = listPrefix.dropLast(SUBLIST_INDENT.length)
        }
    }
}

/**
 * Jsoup will preserve newlines between elements as blank text nodes. These have zero bearing on the content of the
 * document to begin with and only serve to complicate traversal.
 */
private fun Node.stripBlankTextNodes() {
    if (this is TextNode && isBlank) {
        remove()
        return
    }

    childNodes().forEach(Node::stripBlankTextNodes)
}

private fun Node.hasAncestor(predicate: (Node) -> Boolean): Boolean =
    parent()?.let { predicate(it) || it.hasAncestor(predicate) } == true

private fun Node.isList() =
    nodeName().let { it == "ul" || it == "ol" }

private fun Node.isPreformat() =
    nodeName().let { it == "code" || it == "pre" }

private fun TextNode.markdownText() = when {
    // If we're inside a preformat block, everything is literal, ie. no escapes required.
    hasAncestor(Node::isPreformat) -> text()

    // Replace square brackets with escaped equivalents so that they are not rendered as invalid Markdown
    // links.
    else -> text()
        .replace("[", "&#91;")
        .replace("]", "&#93;")
}

/**
 * Operates on all substrings that fall within the provided section delimiters. Returns a new string where all
 * substrings enclosed as specified have been modified according to the provided transform.
 *
 * This extension is not intended to handle nested sections, and will throw if it encounters any.
 */
private fun String.applyWithin(start: String, end: String, transform: (String) -> String): String {
    val startIndex = indexOf(start)
    if (startIndex == -1) return this

    val substringStart = indexOf(start) + start.length
    val substringEnd = indexOf(end, substringStart)
    if (substringEnd == -1) return this

    val stringToTransform = substring(substringStart, substringEnd)
    if (stringToTransform.indexOf(start) != -1) {
        throw CodegenException("string contains nested start delimiter")
    }

    return substring(0, substringStart) + transform(stringToTransform) + end +
        substring(substringEnd + end.length).applyWithin(start, end, transform)
}

private fun String.escapeHtml() =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun StringBuilder.ensureLineBreak() {
    if (!endsWith("\n")) {
        appendLine()
    }
}

private fun StringBuilder.ensureSectionBreak() {
    if (endsWith("\n\n")) return

    if (endsWith("\n")) {
        appendLine()
    } else {
        append("\n\n")
    }
}
