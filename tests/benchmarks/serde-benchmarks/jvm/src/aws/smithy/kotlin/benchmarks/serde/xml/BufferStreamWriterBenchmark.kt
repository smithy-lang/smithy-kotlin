/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.benchmarks.serde.xml

import aws.smithy.kotlin.benchmarks.serde.BenchmarkBase
import aws.smithy.kotlin.runtime.serde.xml.xmlStreamWriter
import aws.smithy.kotlin.runtime.util.InternalApi
import kotlinx.benchmark.*

@OptIn(InternalApi::class)
open class BufferStreamWriterBenchmark : BenchmarkBase() {
    @Benchmark
    fun serializeBenchmark() {
        val writer = xmlStreamWriter()

        writer.startDocument()

        writer.namespacePrefix("https://www.loc.gov/", "loc")
        writer.namespacePrefix("https://en.wikipedia.org/wiki/Library_of_Alexandria", "loa")
        writer.startTag("library")
        writer.attribute("open", "true")

        books.forEach { book ->
            writer.startTag("book")
            writer.attribute("isbn", book.isbn)
            writer.attribute("number", book.locNumber, "loc")

            writer.startTag("title")
            writer.text(book.title)
            writer.endTag("title")

            writer.startTag("authors")
            book.authors.forEach { author ->
                writer.startTag("author")
                writer.text(author)
                writer.endTag("author")
            }
            writer.endTag("authors")

            writer.startTag("subjects")
            book.subjects.forEach { subject ->
                writer.startTag("subject")
                writer.text(subject)
                writer.endTag("subject")
            }
            writer.endTag("subjects")

            writer.endTag("book")
        }

        writer.endTag("library")
        writer.bytes
    }
}

data class Book(
    val isbn: String,
    val title: String,
    val authors: List<String>,
    val subjects: List<String>,
    val locNumber: String,
) {
    fun withIndex(index: Int): Book =
        copy(
            isbn = "$isbn-$index",
            title = "$title-$index",
            authors = authors.map { "$it-$index" },
            subjects = subjects.map { "$it-$index" },
            locNumber = "$locNumber-$index",
        )
}

private val baseBooks = listOf(
    Book(
        isbn = "0439139597",
        title = "Harry Potter and the Goblet of Fire",
        authors = listOf("Rowling, J. K."),
        subjects = listOf("England", "Magic", "School", "Witches", "Wizards"),
        locNumber = "00131084",
    ),
    Book(
        isbn = "0894808532",
        title = "Good Omens: The Nice and Accurate Prophecies of Agnes Nutter, Witch",
        authors = listOf("Pratchett, Terry", "Gaiman, Neil"),
        subjects = listOf("End of the world", "Prophesies", "Witches"),
        locNumber = "90050362",
    ),
    Book(
        isbn = "1400052939",
        title = "The Hitchhiker's Guide to the Galaxy",
        authors = listOf("Adams, Douglas"),
        subjects = listOf("Aliens", "End of the world", "Ultimate Question to Life, the Universe, and Everything"),
        locNumber = "2004558987",
    ),
)
val books = (1..1000).flatMap { index ->
    baseBooks.map { book -> book.withIndex(index) }
}
