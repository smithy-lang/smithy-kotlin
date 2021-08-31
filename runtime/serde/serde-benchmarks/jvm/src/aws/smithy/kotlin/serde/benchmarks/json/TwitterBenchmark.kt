/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.serde.benchmarks.json

import aws.smithy.kotlin.runtime.serde.json.JsonToken
import aws.smithy.kotlin.runtime.serde.json.jsonStreamReader
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import kotlinx.benchmark.*


@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.MILLISECONDS)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class TwitterBenchmark {

    private val input = TwitterBenchmark::class.java.getResource("/twitter.json")!!.readBytes()

    @Benchmark
    fun tokensBenchmark() = runSuspendTest {
        val tokenizer = jsonStreamReader(input)
        do {
            val token = tokenizer.nextToken()
        }while (token != JsonToken.EndDocument)
    }
}

