/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.serde.benchmarks.json

import aws.smithy.kotlin.runtime.serde.json.JsonToken
import aws.smithy.kotlin.runtime.serde.json.jsonStreamReader
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking

@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.MILLISECONDS)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class CitmBenchmark {
    private val input = CitmBenchmark::class.java.getResource("/citm_catalog.json")!!.readBytes()

    @Benchmark
    fun tokensBenchmark() = runBlocking {
        val tokenizer = jsonStreamReader(input)
        do {
            val token = tokenizer.nextToken()
        } while (token != JsonToken.EndDocument)
    }
}
