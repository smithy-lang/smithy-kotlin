/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.serde.xml

import aws.smithy.kotlin.benchmarks.serde.BenchmarkBase
import aws.smithy.kotlin.benchmarks.serde.xml.countriesstates.model.CountriesAndStates
import aws.smithy.kotlin.benchmarks.serde.xml.countriesstates.serde.deserializeCountriesAndStatesDocument
import aws.smithy.kotlin.benchmarks.serde.xml.countriesstates.serde.serializeCountriesAndStatesDocument
import aws.smithy.kotlin.runtime.serde.xml.XmlSerializer
import aws.smithy.kotlin.runtime.serde.xml.root
import aws.smithy.kotlin.runtime.serde.xml.xmlStreamReader
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking

open class XmlSerializerBenchmark : BenchmarkBase() {
    private val source = javaClass.getResource("/countries-states.xml")!!.readBytes()

    private lateinit var dataSet: CountriesAndStates

    @Setup
    fun init() {
        dataSet = runBlocking {
            val deserializer = xmlStreamReader(source).root()
            deserializeCountriesAndStatesDocument(deserializer)
        }
    }

    @Benchmark
    fun serializeBenchmark() {
        val serializer = XmlSerializer()
        serializeCountriesAndStatesDocument(serializer, dataSet)
        serializer.toByteArray()
    }
}
