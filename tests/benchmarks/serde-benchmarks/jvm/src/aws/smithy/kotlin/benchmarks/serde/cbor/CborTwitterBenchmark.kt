/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.benchmarks.serde.cbor

import aws.smithy.kotlin.benchmarks.serde.BenchmarkBase
import aws.smithy.kotlin.benchmarks.serde.cbor.twitter.model.TwitterFeed
import aws.smithy.kotlin.benchmarks.serde.cbor.twitter.serde.deserializeTwitterFeedDocument
import aws.smithy.kotlin.benchmarks.serde.cbor.twitter.serde.serializeTwitterFeedDocument
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.cbor.CborDeserializer
import aws.smithy.kotlin.runtime.serde.cbor.CborSerializer
import aws.smithy.kotlin.runtime.serde.json.JsonStreamReader
import aws.smithy.kotlin.runtime.serde.json.JsonToken
import aws.smithy.kotlin.runtime.serde.json.jsonStreamReader
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking

/**
 * CBOR analog of [aws.smithy.kotlin.benchmarks.serde.json.TwitterBenchmark]. It reuses the exact same
 * `twitter.json` dataset so the CBOR and JSON numbers are comparable.
 *
 * Because the generated CBOR (de)serializers are bound to a distinct model package (with
 * `CborSerialName` descriptors) we cannot feed them the JSON-model instances directly. Instead we
 * transcode the JSON payload into an equivalent CBOR payload once during setup (see [jsonToCbor]) and
 * deserialize that into the CBOR-model [TwitterFeed] used to drive the benchmarks.
 */
open class CborTwitterBenchmark : BenchmarkBase() {
    private val jsonInput = CborTwitterBenchmark::class.java.getResource("/twitter.json")!!.readBytes()

    // A CBOR-encoded equivalent of twitter.json, produced by a generic JSON -> CBOR transcode.
    private val cborInput: ByteArray = jsonToCbor(jsonInput)

    private val feed: TwitterFeed = deserializeTwitterFeedDocument(CborDeserializer(cborInput))

    @Setup
    fun init() {
        // sanity check
        checkNotNull(feed.statuses)
        check(feed.statuses.size == 100) { "expected 100 statuses, got ${feed.statuses.size}" }
        check(feed.statuses[87].createdAt == "Sun Aug 31 00:28:59 +0000 2014")
    }

    @Benchmark
    fun deserializeBenchmark() = runBlocking {
        deserializeTwitterFeedDocument(CborDeserializer(cborInput))
    }

    @Benchmark
    fun serializeBenchmark() = runBlocking {
        val serializer = CborSerializer()
        serializeTwitterFeedDocument(serializer, feed)
        serializer.toByteArray()
    }
}

private val MAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map)
private val LIST_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List)

/**
 * Transcode a JSON payload into an equivalent CBOR payload using the low-level [CborSerializer] API
 * (which requires no field descriptors/traits). Objects become CBOR maps, arrays become CBOR lists,
 * integral numbers become CBOR integers and fractional numbers become CBOR doubles — matching how the
 * generated CBOR serializer would encode the same model.
 */
private fun jsonToCbor(json: ByteArray): ByteArray {
    val reader = jsonStreamReader(json)
    val serializer = CborSerializer()
    transcodeValue(reader, serializer)
    return serializer.toByteArray()
}

private fun transcodeValue(reader: JsonStreamReader, serializer: CborSerializer) {
    when (val token = reader.nextToken()) {
        is JsonToken.BeginObject -> {
            serializer.beginMap(MAP_DESCRIPTOR)
            while (reader.peek() !is JsonToken.EndObject) {
                val name = reader.nextToken() as JsonToken.Name
                serializer.serializeString(name.value)
                transcodeValue(reader, serializer)
            }
            reader.nextToken() // consume EndObject
            serializer.endMap()
        }
        is JsonToken.BeginArray -> {
            serializer.beginList(LIST_DESCRIPTOR)
            while (reader.peek() !is JsonToken.EndArray) {
                transcodeValue(reader, serializer)
            }
            reader.nextToken() // consume EndArray
            serializer.endList()
        }
        is JsonToken.String -> serializer.serializeString(token.value)
        is JsonToken.Number -> {
            val raw = token.value
            if (raw.contains('.') || raw.contains('e') || raw.contains('E')) {
                serializer.serializeDouble(raw.toDouble())
            } else {
                // Some `Integer`-typed model fields (e.g. user ids) carry values that exceed Int range in
                // the real dataset. The CBOR deserializer strictly range-checks (unlike JSON, which
                // saturates via `toDouble().toInt()`), so clamp integral values into Int range to mirror
                // the JSON benchmark's behavior. Value fidelity is irrelevant to a throughput benchmark.
                val clamped = raw.toLong().coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                serializer.serializeLong(clamped)
            }
        }
        is JsonToken.Bool -> serializer.serializeBoolean(token.value)
        is JsonToken.Null -> serializer.serializeNull()
        else -> error("Unexpected JSON token while transcoding to CBOR: $token")
    }
}
