/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.benchmarks.serde.xml

import aws.smithy.kotlin.benchmarks.serde.BenchmarkBase
import aws.smithy.kotlin.runtime.serde.xml.xmlStreamReader
import kotlinx.benchmark.*

open class XmlLexerBenchmark : BenchmarkBase() {
    @Param("countries-states.xml", "kotlin-article.xml")
    var sourceFilename = ""

    private lateinit var sourceBytes: ByteArray

    @Setup
    fun init() {
        sourceBytes = javaClass.getResource("/$sourceFilename")!!.readBytes()
    }

    @Benchmark
    fun deserializeBenchmark() {
        val reader = xmlStreamReader(sourceBytes)
        while (reader.nextToken() != null) { } // Consume the whole stream
    }
}
