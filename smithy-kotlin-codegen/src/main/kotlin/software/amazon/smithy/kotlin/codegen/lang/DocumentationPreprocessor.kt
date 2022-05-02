/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.lang

import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.traits.DocumentationTrait
import software.amazon.smithy.model.transform.ModelTransformer

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.select.NodeTraversor
import org.jsoup.select.NodeVisitor

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
        return renderer.text
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

        var text: String = ""
            private set

        private var bufferedAnchorHref: String = ""
        private var bufferedAnchorText: String = ""
        private var listPrefix: String = ""

        override fun head(node: Node, depth: Int) {
            if (node is TextNode) {
                if (node.parentNode()?.nodeName() == "a")
                    bufferedAnchorText = node.markdownText()
                else
                    text += node.markdownText()
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
                    text += "$listPrefix+ $prefix"
                }
                "code", "pre" -> {
                    text += PREFORMAT_MARKER
                }
                "b", "strong" -> {
                    text += BOLD_MARKER
                }
                "i", "em" -> {
                    text += ITALIC_MARKER
                }
                "ul", "ol" -> {
                    if (node.hasAncestor(Node::isList)) {
                        sublistIndent()
                    }
                }
                "br" -> {
                    text += "\n"
                }
                "dt" -> {
                    // Definition lists (dl, dt, dd) have a corresponding md syntax, but neither intellij nor dokka will
                    // render them. Treat definition terms as a "header" and let the descriptions flow out like
                    // normal markdown content.
                    text += "## "
                }
                "fullname" -> {
                    // Anecdotally this appears to be used to render a "title" display - it always appears at the start
                    // of documents.
                    text += "# "
                }
                "body", "p", "note", "important", "dd", "dl", "div" -> {
                    // Known elements that we can ignore here - they have no bearing on the output.
                }
                else -> {
                    // Occasionally there will be unescaped angle brackets within elements. Those tags will sometimes
                    // join to form "elements" that will trick the rather forgiving Jsoup parser, eg.
                    // "<p>specify the URI in the form 's3://<bucket_name>/</p>"
                    // The safest approach to malformed input like this is just to write out the content as-is, such
                    // that no information is destroyed. The worst outcome is that some seemingly nonsense HTML tag is
                    // injected into the output, which a reader can reasonably ignore.
                    text += "<${node.nodeName()}>"
                }
            }
        }

        override fun tail(node: Node, depth: Int) {
            when (node.nodeName()) {
                "p", "div", "dd" -> {
                    val nextSibling = node.nextSibling()
                    text += when {
                        // break to give the upcoming list a new line
                        (nextSibling != null && nextSibling.isList()) -> "\n"
                        // if we're inside a list, the outer list item will close out the line for us
                        (node.hasAncestor(Node::isList)) -> ""
                        // all other cases: this is a standalone "text block" which should be displayed as its own
                        // paragraph
                        else -> "\n\n"
                    }
                }
                "a" -> {
                    writeBufferedAnchor()
                }
                "ul", "ol" -> {
                    sublistDedent()
                    text += if (node.parent()?.nodeName()  == "body") "\n\n" else ""
                }
                "code", "pre" -> {
                    text += PREFORMAT_MARKER
                }
                "b", "strong" -> {
                    text += BOLD_MARKER
                }
                "i", "em" -> {
                    text += ITALIC_MARKER
                }
                "li", "fullname", "dt" -> {
                    text += "\n"
                }
            }
        }

        private fun writeBufferedAnchor() {
            // Model docs will sometimes contain an anchor without the href. At that point there's no real way of
            // knowing to what it refers, nor can we guarantee a valid link just by bracketing it.
            text += if (bufferedAnchorHref != "") {
                "[$bufferedAnchorText]($bufferedAnchorHref)"
            } else {
                bufferedAnchorText
            }

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
    if (this is TextNode && this.isBlank) {
        this.remove()
        return
    }

    val childNodes = this.childNodes()
    if (childNodes.isNotEmpty()) {
        childNodes.forEach(Node::stripBlankTextNodes)
    }
}

private fun Node.hasAncestor(predicate: (Node) -> Boolean): Boolean {
    if (!this.hasParent()) return false

    val parent = this.parent() as Node
    return predicate(parent) || parent.hasAncestor(predicate)
}

private fun Node.isList() =
    this.nodeName() == "ul" || this.nodeName() == "ol"

private fun TextNode.markdownText() =
    this.text()
        // Replace square brackets with escaped equivalents so that they are not rendered as invalid Markdown
        // links.
        .replace("[", "&#91;")
        .replace("]", "&#93;")

/**
 * Operates on all substrings that fall within the provided section delimiters. Returns a new string where all
 * substrings enclosed as specified have been modified according to the provided transform.
 */
private fun String.applyWithin(start: String, end: String, transform: (String) -> String): String {
    val substringStart = this.indexOf(start) + start.length
    if (substringStart == -1) return this

    val substringEnd = this.indexOf(end, substringStart)
    if (substringEnd == -1) return this

    val stringToTransform = this.substring(substringStart, substringEnd)
    return this.substring(0, substringStart) + transform(stringToTransform) + end +
        this.substring(substringEnd + end.length).applyWithin(start, end, transform)
}

private fun String.escapeHtml() =
    this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")