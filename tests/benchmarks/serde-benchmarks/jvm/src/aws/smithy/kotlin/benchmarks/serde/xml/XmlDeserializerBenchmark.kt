/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.benchmarks.serde.xml

import aws.smithy.kotlin.benchmarks.serde.BenchmarkBase
import aws.smithy.kotlin.benchmarks.serde.xml.countriesstates.model.CountriesAndStates
import aws.smithy.kotlin.benchmarks.serde.xml.countriesstates.serde.deserializeCountriesAndStatesDocument
import aws.smithy.kotlin.runtime.serde.xml.XmlDeserializer
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking

@Measurement(time = 5, timeUnit = BenchmarkTimeUnit.SECONDS)
@Warmup(time = 5, timeUnit = BenchmarkTimeUnit.SECONDS)
open class XmlDeserializerBenchmark : BenchmarkBase() {
    private val source = javaClass.getResource("/countries-states.xml")!!.readBytes()

    private fun deserialize(): CountriesAndStates =
        runBlocking {
            val deserializer = XmlDeserializer(source)
            deserializeCountriesAndStatesDocument(deserializer)
        }

    @Setup
    fun init() {
        // Sanity test
        val result = deserialize()
        val list = checkNotNull(result.countryState)
        check(list.size == 250)
    }

    @Benchmark
    fun deserializeBenchmark() {
        deserialize()
    }
}
