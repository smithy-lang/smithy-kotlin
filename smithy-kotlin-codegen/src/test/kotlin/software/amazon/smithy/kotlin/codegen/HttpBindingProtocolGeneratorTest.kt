/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContainOnlyOnce
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.*
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait

class MockHttpProtocolGenerator : HttpBindingProtocolGenerator() {
    override val defaultTimestampFormat: TimestampFormatTrait.Format = TimestampFormatTrait.Format.EPOCH_SECONDS
    override val defaultContentType: String = "application/json"
    override val protocol: ShapeId = RestJson1Trait.ID

    override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {}
}

// NOTE: protocol conformance is mostly handled by the protocol tests suite
class HttpBindingProtocolGeneratorTest {
    val model: Model = Model.assembler()
        .addImport(javaClass.getResource("http-binding-protocol-generator-test.smithy"))
        .discoverModels()
        .assemble()
        .unwrap()

    data class TestContext(val generationCtx: ProtocolGenerator.GenerationContext, val manifest: MockManifest, val generator: MockHttpProtocolGenerator)

    private fun newTestContext(): TestContext {
        val settings = KotlinSettings.from(model, Node.objectNodeBuilder()
            .withMember("module", Node.from("test"))
            .withMember("moduleVersion", Node.from("1.0.0"))
            .build())
        val manifest = MockManifest()
        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test")
        val service = model.getShape(ShapeId.from("com.test#Example")).get().asServiceShape().get()
        val delegator = KotlinDelegator(settings, model, manifest, provider)
        val generator = MockHttpProtocolGenerator()
        val ctx = ProtocolGenerator.GenerationContext(settings, model, service, provider, listOf(), generator.protocol, delegator)
        return TestContext(ctx, manifest, generator)
    }

    @Test
    fun `it creates serialize transforms in correct package`() {
        val (ctx, manifest, generator) = newTestContext()
        generator.generateSerializers(ctx)
        ctx.delegator.flushWriters()
        assertTrue(manifest.hasFile("src/main/kotlin/test/transform/SmokeTestSerializer.kt"))
    }

    @Test
    fun `it creates serialize transforms for nested structures`() {
        // test that a struct member of an input operation shape also gets a serializer
        val (ctx, manifest, generator) = newTestContext()
        generator.generateSerializers(ctx)
        ctx.delegator.flushWriters()
        assertTrue(manifest.hasFile("src/main/kotlin/test/transform/NestedSerializer.kt"))
        // these are non-top level shapes reachable from an operation input and thus require a serializer
        assertTrue(manifest.hasFile("src/main/kotlin/test/transform/Nested2Serializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/test/transform/Nested3Serializer.kt"))
        assertTrue(manifest.hasFile("src/main/kotlin/test/transform/Nested4Serializer.kt"))
    }

    private fun getTransformFileContents(filename: String): String {
        val (ctx, manifest, generator) = newTestContext()
        generator.generateSerializers(ctx)
        generator.generateDeserializers(ctx)
        ctx.delegator.flushWriters()
        return getTransformFileContents(manifest, filename)
    }

    private fun getTransformFileContents(manifest: MockManifest, filename: String): String {
        return manifest.expectFileString("src/main/kotlin/test/transform/$filename")
    }

    @Test
    fun `it creates smoke test request serializer`() {
        val contents = getTransformFileContents("SmokeTestSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val label1 = "\${input.label1}" // workaround for raw strings not being able to contain escapes
        val expectedContents = """
class SmokeTestSerializer(val input: SmokeTestRequest) : HttpSerialize {

    companion object {
        private val PAYLOAD1_DESCRIPTOR = SdkFieldDescriptor("payload1")
        private val PAYLOAD2_DESCRIPTOR = SdkFieldDescriptor("payload2")
        private val PAYLOAD3_DESCRIPTOR = SdkFieldDescriptor("payload3")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(PAYLOAD1_DESCRIPTOR)
            field(PAYLOAD2_DESCRIPTOR)
            field(PAYLOAD3_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/smoketest/$label1/foo"
            parameters {
                if (input.query1 != null) append("Query1", input.query1)
            }
        }

        builder.headers {
            append("Content-Type", "application/json")
            if (input.header1?.isNotEmpty() == true) append("X-Header1", input.header1)
            if (input.header2?.isNotEmpty() == true) append("X-Header2", input.header2)
        }

        val serializer = provider()
        serializer.serializeStruct {
            input.payload1?.let { field(PAYLOAD1_DESCRIPTOR, it) }
            input.payload2?.let { field(PAYLOAD2_DESCRIPTOR, it) }
            input.payload3?.let { field(PAYLOAD3_DESCRIPTOR, NestedSerializer(it)) }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        // NOTE: SmokeTestRequest$payload3 is a struct itself, the Serializer interface handles this if the type
        // implements `SdkSerializable`
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes explicit string payloads`() {
        val contents = getTransformFileContents("ExplicitStringSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class ExplicitStringSerializer(val input: ExplicitStringRequest) : HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/string"
        }

        builder.headers {
            append("Content-Type", "text/plain")
        }

        if (input.payload1 != null) {
            builder.body = ByteArrayContent(input.payload1.toByteArray())
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes explicit blob payloads`() {
        val contents = getTransformFileContents("ExplicitBlobSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class ExplicitBlobSerializer(val input: ExplicitBlobRequest) : HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/blob"
        }

        builder.headers {
            append("Content-Type", "application/octet-stream")
        }

        if (input.payload1 != null) {
            builder.body = ByteArrayContent(input.payload1)
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes explicit streaming blob payloads`() {
        val contents = getTransformFileContents("ExplicitBlobStreamSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class ExplicitBlobStreamSerializer(val input: ExplicitBlobStreamRequest) : HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/blobstream"
        }

        builder.headers {
            append("Content-Type", "application/octet-stream")
        }

        if (input.payload1 != null) {
            builder.body = input.payload1.toHttpBody() ?: HttpBody.Empty
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes explicit struct payloads`() {
        val contents = getTransformFileContents("ExplicitStructSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class ExplicitStructSerializer(val input: ExplicitStructRequest) : HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/explicit/struct"
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

        if (input.payload1 != null) {
            val serializer = provider()
            Nested2Serializer(input.payload1).serialize(serializer)
            builder.body = ByteArrayContent(serializer.toByteArray())
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes operation inputs with lists`() {
        val contents = getTransformFileContents("ListInputSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class ListInputSerializer(val input: ListInputRequest) : HttpSerialize {

    companion object {
        private val BLOBLIST_DESCRIPTOR = SdkFieldDescriptor("blobList")
        private val ENUMLIST_DESCRIPTOR = SdkFieldDescriptor("enumList")
        private val INTLIST_DESCRIPTOR = SdkFieldDescriptor("intList")
        private val NESTEDINTLIST_DESCRIPTOR = SdkFieldDescriptor("nestedIntList")
        private val STRUCTLIST_DESCRIPTOR = SdkFieldDescriptor("structList")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(BLOBLIST_DESCRIPTOR)
            field(ENUMLIST_DESCRIPTOR)
            field(INTLIST_DESCRIPTOR)
            field(NESTEDINTLIST_DESCRIPTOR)
            field(STRUCTLIST_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/list"
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

        val serializer = provider()
        serializer.serializeStruct {
            if (input.blobList != null) {
                listField(BLOBLIST_DESCRIPTOR) {
                    for(m0 in input.blobList) {
                        serializeString(m0.encodeBase64String())
                    }
                }
            }
            if (input.enumList != null) {
                listField(ENUMLIST_DESCRIPTOR) {
                    for(m0 in input.enumList) {
                        serializeString(m0.value)
                    }
                }
            }
            if (input.intList != null) {
                listField(INTLIST_DESCRIPTOR) {
                    for(m0 in input.intList) {
                        serializeInt(m0)
                    }
                }
            }
            if (input.nestedIntList != null) {
                listField(NESTEDINTLIST_DESCRIPTOR) {
                    for(m0 in input.nestedIntList) {
                        serializer.serializeList {
                            for(m1 in m0) {
                                serializeInt(m1)
                            }
                        }
                    }
                }
            }
            if (input.structList != null) {
                listField(STRUCTLIST_DESCRIPTOR) {
                    for(m0 in input.structList) {
                        serializeSdkSerializable(NestedSerializer(m0))
                    }
                }
            }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes nested documents with aggregate shapes`() {
        // non operational input (nested member somewhere in the graph) that has a list/map shape
        val contents = getTransformFileContents("Nested4Serializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class Nested4Serializer(val input: Nested4) : SdkSerializable {

    companion object {
        private val INTLIST_DESCRIPTOR = SdkFieldDescriptor("intList")
        private val INTMAP_DESCRIPTOR = SdkFieldDescriptor("intMap")
        private val MEMBER1_DESCRIPTOR = SdkFieldDescriptor("member1")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(INTLIST_DESCRIPTOR)
            field(INTMAP_DESCRIPTOR)
            field(MEMBER1_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct {
            if (input.intList != null) {
                listField(INTLIST_DESCRIPTOR) {
                    for(m0 in input.intList) {
                        serializeInt(m0)
                    }
                }
            }
            if (input.intMap != null) {
                mapField(INTMAP_DESCRIPTOR) {
                    input.intMap.forEach { (key, value) -> entry(key, value) }
                }
            }
            input.member1?.let { field(MEMBER1_DESCRIPTOR, it) }
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
        contents.shouldContainOnlyOnce("import test.model.Nested4")
    }

    @Test
    fun `it serializes nested documents with struct members`() {
        // non operational input (nested member somewhere in the graph) that has another non-operational struct as a member
        val contents = getTransformFileContents("Nested3Serializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class Nested3Serializer(val input: Nested3) : SdkSerializable {

    companion object {
        private val MEMBER1_DESCRIPTOR = SdkFieldDescriptor("member1")
        private val MEMBER2_DESCRIPTOR = SdkFieldDescriptor("member2")
        private val MEMBER3_DESCRIPTOR = SdkFieldDescriptor("member3")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(MEMBER1_DESCRIPTOR)
            field(MEMBER2_DESCRIPTOR)
            field(MEMBER3_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct {
            input.member1?.let { field(MEMBER1_DESCRIPTOR, it) }
            input.member2?.let { field(MEMBER2_DESCRIPTOR, it) }
            input.member3?.let { field(MEMBER3_DESCRIPTOR, Nested4Serializer(it)) }
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
        contents.shouldContainOnlyOnce("import test.model.Nested3")
    }

    @Test
    fun `it serializes maps with nested struct members`() {
        val (ctx, manifest, generator) = newTestContext()
        generator.generateSerializers(ctx)
        ctx.delegator.flushWriters()
        // serializer should exist for the map value `ReachableOnlyThroughMap`
        assertTrue(manifest.hasFile("src/main/kotlin/test/transform/ReachableOnlyThroughMapSerializer.kt"))
        val contents = getTransformFileContents(manifest, "MapInputSerializer.kt")
        contents.shouldSyntacticSanityCheck()

        val expectedContents = """
class MapInputSerializer(val input: MapInputRequest) : HttpSerialize {

    companion object {
        private val BLOBMAP_DESCRIPTOR = SdkFieldDescriptor("blobMap")
        private val ENUMMAP_DESCRIPTOR = SdkFieldDescriptor("enumMap")
        private val INTMAP_DESCRIPTOR = SdkFieldDescriptor("intMap")
        private val STRUCTMAP_DESCRIPTOR = SdkFieldDescriptor("structMap")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(BLOBMAP_DESCRIPTOR)
            field(ENUMMAP_DESCRIPTOR)
            field(INTMAP_DESCRIPTOR)
            field(STRUCTMAP_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/map"
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

        val serializer = provider()
        serializer.serializeStruct {
            if (input.blobMap != null) {
                mapField(BLOBMAP_DESCRIPTOR) {
                    input.blobMap.forEach { (key, value) -> entry(key, value.encodeBase64String()) }
                }
            }
            if (input.enumMap != null) {
                mapField(ENUMMAP_DESCRIPTOR) {
                    input.enumMap.forEach { (key, value) -> entry(key, value.value) }
                }
            }
            if (input.intMap != null) {
                mapField(INTMAP_DESCRIPTOR) {
                    input.intMap.forEach { (key, value) -> entry(key, value) }
                }
            }
            if (input.structMap != null) {
                mapField(STRUCTMAP_DESCRIPTOR) {
                    input.structMap.forEach { (key, value) -> entry(key, ReachableOnlyThroughMapSerializer(value)) }
                }
            }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
        contents.shouldContainOnlyOnce("import test.model.MapInputRequest")
    }

    @Test
    fun `it serializes enums as raw values`() {
        val contents = getTransformFileContents("NestedEnumSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class NestedEnumSerializer(val input: NestedEnum) : SdkSerializable {

    companion object {
        private val MYENUM_DESCRIPTOR = SdkFieldDescriptor("myEnum")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(MYENUM_DESCRIPTOR)
        }
    }

    override fun serialize(serializer: Serializer) {
        serializer.serializeStruct {
            input.myEnum?.let { field(MYENUM_DESCRIPTOR, it.value) }
        }
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes operation inputs with enums`() {
        val contents = getTransformFileContents("EnumInputSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class EnumInputSerializer(val input: EnumInputRequest) : HttpSerialize {

    companion object {
        private val NESTEDWITHENUM_DESCRIPTOR = SdkFieldDescriptor("nestedWithEnum")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(NESTEDWITHENUM_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/enum"
        }

        builder.headers {
            append("Content-Type", "application/json")
            if (input.enumHeader != null) append("X-EnumHeader", input.enumHeader.value)
        }

        val serializer = provider()
        serializer.serializeStruct {
            input.nestedWithEnum?.let { field(NESTEDWITHENUM_DESCRIPTOR, NestedEnumSerializer(it)) }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it serializes operation inputs with timestamps`() {
        val contents = getTransformFileContents("TimestampInputSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val tsLabel = "\${input.tsLabel?.format(TimestampFormat.ISO_8601)}" // workaround for raw strings not being able to contain escapes
        val expectedContents = """
class TimestampInputSerializer(val input: TimestampInputRequest) : HttpSerialize {

    companion object {
        private val DATETIME_DESCRIPTOR = SdkFieldDescriptor("dateTime")
        private val EPOCHSECONDS_DESCRIPTOR = SdkFieldDescriptor("epochSeconds")
        private val HTTPDATE_DESCRIPTOR = SdkFieldDescriptor("httpDate")
        private val NORMAL_DESCRIPTOR = SdkFieldDescriptor("normal")
        private val TIMESTAMPLIST_DESCRIPTOR = SdkFieldDescriptor("timestampList")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(DATETIME_DESCRIPTOR)
            field(EPOCHSECONDS_DESCRIPTOR)
            field(HTTPDATE_DESCRIPTOR)
            field(NORMAL_DESCRIPTOR)
            field(TIMESTAMPLIST_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/timestamp/$tsLabel"
            parameters {
                if (input.queryTimestamp != null) append("qtime", input.queryTimestamp.format(TimestampFormat.ISO_8601))
                if (input.queryTimestampList?.isNotEmpty() == true) appendAll("qtimeList", input.queryTimestampList.map { it.format(TimestampFormat.ISO_8601) })
            }
        }

        builder.headers {
            append("Content-Type", "application/json")
            if (input.headerEpoch != null) append("X-Epoch", input.headerEpoch.format(TimestampFormat.EPOCH_SECONDS))
            if (input.headerHttpDate != null) append("X-Date", input.headerHttpDate.format(TimestampFormat.RFC_5322))
        }

        val serializer = provider()
        serializer.serializeStruct {
            input.dateTime?.let { field(DATETIME_DESCRIPTOR, it.format(TimestampFormat.ISO_8601)) }
            input.epochSeconds?.let { rawField(EPOCHSECONDS_DESCRIPTOR, it.format(TimestampFormat.EPOCH_SECONDS)) }
            input.httpDate?.let { field(HTTPDATE_DESCRIPTOR, it.format(TimestampFormat.RFC_5322)) }
            input.normal?.let { rawField(NORMAL_DESCRIPTOR, it.format(TimestampFormat.EPOCH_SECONDS)) }
            if (input.timestampList != null) {
                listField(TIMESTAMPLIST_DESCRIPTOR) {
                    for(m0 in input.timestampList) {
                        serializeRaw(m0.format(TimestampFormat.EPOCH_SECONDS))
                    }
                }
            }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
        contents.shouldContainOnlyOnce("import software.aws.clientrt.time.TimestampFormat")
    }

    @Test
    fun `it creates blob input request serializer`() {
        // base64 encoding is protocol dependent. The mock protocol generator is based on
        // json protocol though which does encode to base64
        val contents = getTransformFileContents("BlobInputSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class BlobInputSerializer(val input: BlobInputRequest) : HttpSerialize {

    companion object {
        private val PAYLOADBLOB_DESCRIPTOR = SdkFieldDescriptor("payloadBlob")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(PAYLOADBLOB_DESCRIPTOR)
        }
    }

    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.POST

        builder.url {
            path = "/input/blob"
            parameters {
                if (input.queryBlob?.isNotEmpty() == true) append("qblob", input.queryBlob.encodeBase64String())
            }
        }

        builder.headers {
            append("Content-Type", "application/json")
            if (input.headerMediaType?.isNotEmpty() == true) append("X-Blob", input.headerMediaType.encodeBase64())
        }

        val serializer = provider()
        serializer.serializeStruct {
            input.payloadBlob?.let { field(PAYLOADBLOB_DESCRIPTOR, it.encodeBase64String()) }
        }

        builder.body = ByteArrayContent(serializer.toByteArray())
    }
}
"""
        // NOTE: SmokeTestRequest$payload3 is a struct itself, the Serializer interface handles this if the type
        // implements `SdkSerializable`
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it handles query string literals`() {
        val contents = getTransformFileContents("ConstantQueryStringSerializer.kt")
        contents.shouldSyntacticSanityCheck()
        val label1 = "\${input.hello}" // workaround for raw strings not being able to contain escapes
        val expectedContents = """
class ConstantQueryStringSerializer(val input: ConstantQueryStringInput) : HttpSerialize {
    override suspend fun serialize(builder: HttpRequestBuilder, provider: SerializationProvider) {
        builder.method = HttpMethod.GET

        builder.url {
            path = "/ConstantQueryString/$label1"
            parameters {
                append("foo", "bar")
                append("hello", "")
            }
        }

        builder.headers {
            append("Content-Type", "application/json")
        }

    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it creates smoke test response deserializer`() {
        val contents = getTransformFileContents("SmokeTestDeserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class SmokeTestDeserializer : HttpDeserialize {

    companion object {
        private val PAYLOAD1_DESCRIPTOR = SdkFieldDescriptor("payload1")
        private val PAYLOAD2_DESCRIPTOR = SdkFieldDescriptor("payload2")
        private val PAYLOAD3_DESCRIPTOR = SdkFieldDescriptor("payload3")
        private val PAYLOAD4_DESCRIPTOR = SdkFieldDescriptor("payload4")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(PAYLOAD1_DESCRIPTOR)
            field(PAYLOAD2_DESCRIPTOR)
            field(PAYLOAD3_DESCRIPTOR)
            field(PAYLOAD4_DESCRIPTOR)
        }
    }

    override suspend fun deserialize(response: HttpResponse, provider: DeserializationProvider): SmokeTestResponse {
        val builder = SmokeTestResponse.dslBuilder()

        builder.intHeader = response.headers["X-Header2"]?.toInt()
        builder.strHeader = response.headers["X-Header1"]
        builder.tsListHeader = response.headers.getAll("X-Header3")?.flatMap(::splitHttpDateHeaderListValues)?.map { Instant.fromRfc5322(it) }

        val payload = response.body.readAll()
        if (payload != null) {
            val deserializer = provider(payload)
            deserializer.deserializeStruct(null) {
                loop@while(true) {
                    when(nextField(OBJ_DESCRIPTOR)) {
                        PAYLOAD1_DESCRIPTOR.index -> builder.payload1 = deserializeString()
                        PAYLOAD2_DESCRIPTOR.index -> builder.payload2 = deserializeInt()
                        PAYLOAD3_DESCRIPTOR.index -> builder.payload3 = NestedDeserializer().deserialize(deserializer)
                        PAYLOAD4_DESCRIPTOR.index -> builder.payload4 = Instant.fromIso8601(deserializeString())
                        Deserializer.FieldIterator.EXHAUSTED -> break@loop
                        else -> skipValue()
                    }
                }
            }
        }
        return builder.build()
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it deserializes prefix headers`() {
        val contents = getTransformFileContents("PrefixHeadersDeserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
        val keysForMember1 = response.headers.names().filter { it.startsWith("X-Foo-") }
        if (keysForMember1.isNotEmpty()) {
            val map = mutableMapOf<String, String>()
            for (hdrKey in keysForMember1) {
                val el = response.headers[hdrKey] ?: continue
                val key = hdrKey.removePrefix("X-Foo-")
                map[key] = el
            }
            builder.member1 = map
        }
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it deserializes explicit string payloads`() {
        val contents = getTransformFileContents("ExplicitStringDeserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
        val contents = response.body.readAll()?.decodeToString()
        builder.payload1 = contents
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it deserializes explicit blob payloads`() {
        val contents = getTransformFileContents("ExplicitBlobDeserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
        builder.payload1 = response.body.readAll()
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it deserializes explicit streaming blob payloads`() {
        val contents = getTransformFileContents("ExplicitBlobStreamDeserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
        builder.payload1 = response.body.toByteStream()
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it deserializes explicit struct payloads`() {
        val contents = getTransformFileContents("ExplicitStructDeserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
        val payload = response.body.readAll()
        if (payload != null) {
            val deserializer = provider(payload)
            builder.payload1 = Nested2Deserializer().deserialize(deserializer)
        }
"""
        contents.shouldContainOnlyOnce(expectedContents)
    }

    @Test
    fun `it deserializes nested documents with struct members`() {
        // non operational output (nested member somewhere in the graph) that has another non-operational struct as a member
        val contents = getTransformFileContents("Nested3Deserializer.kt")
        contents.shouldSyntacticSanityCheck()
        val expectedContents = """
class Nested3Deserializer {

    companion object {
        private val MEMBER1_DESCRIPTOR = SdkFieldDescriptor("member1")
        private val MEMBER2_DESCRIPTOR = SdkFieldDescriptor("member2")
        private val MEMBER3_DESCRIPTOR = SdkFieldDescriptor("member3")
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
            field(MEMBER1_DESCRIPTOR)
            field(MEMBER2_DESCRIPTOR)
            field(MEMBER3_DESCRIPTOR)
        }
    }

    fun deserialize(deserializer: Deserializer): Nested3 {
        val builder = Nested3.dslBuilder()
        deserializer.deserializeStruct(null) {
            loop@while(true) {
                when(nextField(OBJ_DESCRIPTOR)) {
                    MEMBER1_DESCRIPTOR.index -> builder.member1 = deserializeString()
                    MEMBER2_DESCRIPTOR.index -> builder.member2 = deserializeString()
                    MEMBER3_DESCRIPTOR.index -> builder.member3 = Nested4Deserializer().deserialize(deserializer)
                    Deserializer.FieldIterator.EXHAUSTED -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}
"""
        contents.shouldContainOnlyOnce(expectedContents)
        contents.shouldContainOnlyOnce("import test.model.Nested3")
    }
}
