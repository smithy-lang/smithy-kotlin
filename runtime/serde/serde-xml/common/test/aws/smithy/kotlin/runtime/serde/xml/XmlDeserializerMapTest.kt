/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*
import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Test

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerMapTest {

    @Test
    fun itHandlesMapsWithDefaultNodeNames() {
        val payload = """
            <object>
                <values>
                    <entry>
                        <key>key1</key>
                        <value>1</value>
                    </entry>
                    <entry>
                        <key>key2</key>
                        <value>2</value>
                    </entry>
                </values>
            </object>
        """.encodeToByteArray()
        val fieldDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }

        var actual = mutableMapOf<String, Int>()
        val deserializer = XmlDeserializer(payload)
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index ->
                        actual =
                            deserializer.deserializeMap(fieldDescriptor) {
                                val map0 = mutableMapOf<String, Int>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        val expected = mapOf("key1" to 1, "key2" to 2)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesMapsWithCustomNodeNames() {
        val payload = """
            <object>
                <mymap>
                    <myentry>
                        <mykey>key1</mykey>
                        <myvalue>1</myvalue>
                    </myentry>
                    <myentry>
                        <mykey>key2</mykey>
                        <myvalue>2</myvalue>
                    </myentry>
                </mymap>
            </object>
        """.encodeToByteArray()
        val fieldDescriptor =
            SdkFieldDescriptor(SerialKind.Map, XmlSerialName("mymap"), XmlMapName("myentry", "mykey", "myvalue"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }
        var actual = mutableMapOf<String, Int>()
        val deserializer = XmlDeserializer(payload)
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index ->
                        actual =
                            deserializer.deserializeMap(fieldDescriptor) {
                                val map0 = mutableMapOf<String, Int>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        val expected = mapOf("key1" to 1, "key2" to 2)
        actual.shouldContainExactly(expected)
    }

    // https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#flattened-map-serialization
    @Test
    fun itHandlesFlatMaps() {
        val payload = """
            <object>
                <flatMap>
                    <key>key1</key>
                    <value>1</value>
                </flatMap>
                <flatMap>
                    <key>key2</key>
                    <value>2</value>
                </flatMap>
                <flatMap>
                    <key>key3</key>
                    <value>3</value>
                </flatMap>
            </object>
        """.encodeToByteArray()
        val containerFieldDescriptor =
            SdkFieldDescriptor(SerialKind.Map, XmlSerialName("flatMap"), XmlMapName(null, "key", "value"), Flattened)
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(containerFieldDescriptor)
        }
        var actual = mutableMapOf<String, Int>()
        val deserializer = XmlDeserializer(payload)
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    containerFieldDescriptor.index ->
                        actual =
                            deserializer.deserializeMap(containerFieldDescriptor) {
                                val map0 = mutableMapOf<String, Int>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        val expected = mapOf("key1" to 1, "key2" to 2, "key3" to 3)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesEmptyMaps() {
        val payload = """
            <object>
                <map />
            </object>
        """.encodeToByteArray()
        val containerFieldDescriptor =
            SdkFieldDescriptor(SerialKind.Map, XmlSerialName("Map"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(containerFieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual = mutableMapOf<String, Int>()
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    containerFieldDescriptor.index ->
                        actual =
                            deserializer.deserializeMap(containerFieldDescriptor) {
                                val map0 = mutableMapOf<String, Int>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        val expected = emptyMap<String, Int>()
        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesSparseMaps() {
        val payload = """
            <object>
                <values>
                    <entry>
                        <key>key1</key>
                        <value>1</value>
                    </entry>
                    <entry>
                        <key>key2</key>
                        <value></value>
                    </entry>
                </values>
            </object>
        """.encodeToByteArray()
        val fieldDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual = mutableMapOf<String, Int?>()
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index ->
                        actual =
                            deserializer.deserializeMap(fieldDescriptor) {
                                val map = mutableMapOf<String, Int?>()
                                while (hasNextEntry()) {
                                    val key = key()
                                    val value = when (nextHasValue()) {
                                        true -> deserializeInt()
                                        false -> deserializeNull()
                                    }

                                    map[key] = value
                                }
                                return@deserializeMap map
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        val expected = mapOf("key1" to 1, "key2" to null)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesCheckingMapValuesForNull() {
        val payload = """
            <object>
                <values>
                    <entry>
                        <key>key1</key>
                        <value>1</value>
                    </entry>
                    <entry>
                        <key>key2</key>
                        <value></value>
                    </entry>
                </values>
            </object>
        """.encodeToByteArray()
        val fieldDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual = mutableMapOf<String, Int>()
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index ->
                        actual =
                            deserializer.deserializeMap(fieldDescriptor) {
                                val map0 = mutableMapOf<String, Int>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        val expected = mapOf("key1" to 1)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesNestedMap() {
        val payload = """
            <object>
                <map>
                    <outerEntry>
                        <key>outer1</key>
                        <outerValue>
                            <nestedMap>
                                <innerEntry>
                                    <key>inner1</key>
                                    <innerValue>innerValue1</innerValue>
                                </innerEntry>
                                <innerEntry>
                                    <key>inner2</key>
                                    <innerValue>innerValue2</innerValue>
                                </innerEntry>
                            </nestedMap>
                        </outerValue>
                    </outerEntry>
                    <outerEntry>
                        <key>outer2</key>
                        <outerValue>
                            <nestedMap>
                                <innerEntry>
                                    <key>inner3</key>
                                    <innerValue>innerValue3</innerValue>
                                </innerEntry>
                                <innerEntry>
                                    <key>inner4</key>
                                    <innerValue>innerValue4</innerValue>
                                </innerEntry>
                            </nestedMap>
                        </outerValue>
                    </outerEntry>
                </map>
            </object>
        """.encodeToByteArray()
        val ELEMENT_MAP_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("map"), XmlMapName(entry = "outerEntry", value = "outerValue"))
        val nestedMapDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("nestedMap"), XmlMapName(entry = "innerEntry", value = "innerValue"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_MAP_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        var actual = mutableMapOf<String, Map<String, String>>()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    ELEMENT_MAP_FIELD_DESCRIPTOR.index ->
                        actual =
                            deserializer.deserializeMap(ELEMENT_MAP_FIELD_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, Map<String, String>>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = deserializer.deserializeMap(nestedMapDescriptor) {
                                        val map1 = mutableMapOf<String, String>()
                                        while (hasNextEntry()) {
                                            val k1 = key()
                                            val v1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                            map1[k1] = v1
                                        }
                                        map1
                                    }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        val expected = mapOf(
            "outer1" to mapOf("inner1" to "innerValue1", "inner2" to "innerValue2"),
            "outer2" to mapOf("inner3" to "innerValue3", "inner4" to "innerValue4"),
        )

        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesNestedStructAsValue() {
        val payload = """
            <XmlMapsInputOutput>
                <myMap>
                    <entry>
                        <key>foo</key>
                        <value>
                            <hi>there</hi>
                        </value>
                    </entry>
                    <entry>
                        <key>baz</key>
                        <value>
                            <hi>bye</hi>
                        </value>
                    </entry>
                </myMap>
            </XmlMapsInputOutput>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val resp = XmlMapsOperationDeserializer().deserialize(deserializer)

        println(resp)
    }
}

internal class XmlMapsOperationDeserializer() {

    companion object {
        private val MYMAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("myMap"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("XmlMapsInputOutput"))
            field(MYMAP_DESCRIPTOR)
        }
    }

    fun deserialize(deserializer: XmlDeserializer): XmlMapsInputOutput {
        val builder = XmlMapsInputOutput.dslBuilder()

        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    MYMAP_DESCRIPTOR.index ->
                        builder.myMap =
                            deserializer.deserializeMap(MYMAP_DESCRIPTOR) {
                                val map0 = mutableMapOf<String, GreetingStruct>()
                                while (hasNextEntry()) {
                                    val k0 = key()
                                    val v0 = if (nextHasValue()) { GreetingStructDocumentDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
                                    map0[k0] = v0
                                }
                                map0
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        return builder.build()
    }
}

internal class GreetingStructDocumentDeserializer {

    companion object {
        private val HI_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("hi"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("GreetingStruct"))
            field(HI_DESCRIPTOR)
        }
    }

    fun deserialize(deserializer: Deserializer): GreetingStruct {
        val builder = GreetingStruct.dslBuilder()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    HI_DESCRIPTOR.index -> builder.hi = deserializeString()
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}

class XmlMapsInputOutput private constructor(builder: BuilderImpl) {
    val myMap: Map<String, GreetingStruct>? = builder.myMap

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): XmlMapsInputOutput = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("XmlMapsInputOutput(")
        append("myMap=$myMap)")
    }

    override fun hashCode(): kotlin.Int {
        var result = myMap?.hashCode() ?: 0
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as XmlMapsInputOutput

        if (myMap != other.myMap) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): XmlMapsInputOutput = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): XmlMapsInputOutput
        fun myMap(myMap: Map<String, GreetingStruct>): Builder
    }

    interface DslBuilder {
        var myMap: Map<String, GreetingStruct>?

        fun build(): XmlMapsInputOutput
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var myMap: Map<String, GreetingStruct>? = null

        constructor(x: XmlMapsInputOutput) : this() {
            this.myMap = x.myMap
        }

        override fun build(): XmlMapsInputOutput = XmlMapsInputOutput(this)
        override fun myMap(myMap: Map<String, GreetingStruct>): Builder = apply { this.myMap = myMap }
    }
}

class GreetingStruct private constructor(builder: BuilderImpl) {
    val hi: String? = builder.hi

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): GreetingStruct = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("GreetingStruct(")
        append("hi=$hi)")
    }

    override fun hashCode(): kotlin.Int {
        var result = hi?.hashCode() ?: 0
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as GreetingStruct

        if (hi != other.hi) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): GreetingStruct = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): GreetingStruct
        fun hi(hi: String): Builder
    }

    interface DslBuilder {
        var hi: String?

        fun build(): GreetingStruct
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var hi: String? = null

        constructor(x: GreetingStruct) : this() {
            this.hi = x.hi
        }

        override fun build(): GreetingStruct = GreetingStruct(this)
        override fun hi(hi: String): Builder = apply { this.hi = hi }
    }
}
