/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package aws.smithy.kotlin.benchmarks.serde.json

import aws.smithy.kotlin.benchmarks.serde.json.twitter.model.TwitterFeed
import aws.smithy.kotlin.benchmarks.serde.json.twitter.transform.deserializeTwitterFeedDocument
import aws.smithy.kotlin.benchmarks.serde.json.twitter.transform.serializeTwitterFeedDocument
import aws.smithy.kotlin.runtime.serde.json.JsonDeserializer
import aws.smithy.kotlin.runtime.serde.json.JsonSerializer
import aws.smithy.kotlin.runtime.serde.json.JsonToken
import aws.smithy.kotlin.runtime.serde.json.jsonStreamReader
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking

@Warmup(iterations = 7, time = 1)
@Measurement(iterations = 5, time = 1, timeUnit = BenchmarkTimeUnit.MILLISECONDS)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
open class TwitterBenchmark {

    private val input = TwitterBenchmark::class.java.getResource("/twitter.json")!!.readBytes()
    private val feed: TwitterFeed = runBlocking {
        val deserializer = JsonDeserializer(input)
        deserializeTwitterFeedDocument(deserializer)
    }

    @Setup
    fun init() {
        // sanity check
        checkNotNull(feed.statuses)
        check(feed.statuses.size == 100)
        check(feed.statuses[87].createdAt == "Sun Aug 31 00:28:59 +0000 2014")
    }

    @Benchmark
    fun tokensBenchmark() = runBlocking {
        val tokenizer = jsonStreamReader(input)
        do {
            val token = tokenizer.nextToken()
        } while (token != JsonToken.EndDocument)
    }

    @Benchmark
    fun deserializeBenchmark() = runBlocking {
        val deserializer = JsonDeserializer(input)
        deserializeTwitterFeedDocument(deserializer)
    }

    @Benchmark
    fun serializeBenchmark() = runBlocking {
        val serializer = JsonSerializer()
        serializeTwitterFeedDocument(serializer, feed)
    }
}
