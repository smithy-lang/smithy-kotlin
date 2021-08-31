/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.serde.benchmarks.json

import aws.smithy.kotlin.runtime.serde.json.JsonDeserializer
import aws.smithy.kotlin.runtime.serde.json.JsonToken
import aws.smithy.kotlin.runtime.serde.json.jsonStreamReader
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import aws.smithy.kotlin.serde.benchmarks.model.twitter.TwitterFeed
import aws.smithy.kotlin.serde.benchmarks.model.twitter.deserializeTwitterFeedDocument
import kotlinx.benchmark.*


@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.MILLISECONDS)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class TwitterBenchmark {

    private val input = TwitterBenchmark::class.java.getResource("/twitter.json")!!.readBytes()
    private val feed: TwitterFeed

    init {
        feed = runSuspendTest {
            val deserializer = JsonDeserializer(input)
            deserializeTwitterFeedDocument(deserializer)
        }
    }

    @Setup
    fun init() = runSuspendTest {
        // sanity check
        checkNotNull(feed.statuses)
        check(feed.statuses.size == 100)
        check(feed.statuses[87].createdAt == "Sun Aug 31 00:28:59 +0000 2014")
    }

    @Benchmark
    fun tokensBenchmark() = runSuspendTest {
        val tokenizer = jsonStreamReader(input)
        do {
            val token = tokenizer.nextToken()
        }while (token != JsonToken.EndDocument)
    }

    @Benchmark
    fun deserializeBenchmark() = runSuspendTest {
        val deserializer = JsonDeserializer(input)
        deserializeTwitterFeedDocument(deserializer)
    }
}

