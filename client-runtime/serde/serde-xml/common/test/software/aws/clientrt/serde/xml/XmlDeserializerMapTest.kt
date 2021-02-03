/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import io.kotest.matchers.maps.shouldContainExactly
import software.aws.clientrt.serde.*
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
        val fieldDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"), XmlMap())
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }

        var actual = mutableMapOf<String, Int>()
        val deserializer = XmlDeserializer2(payload)
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index -> actual =
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
            SdkFieldDescriptor(SerialKind.Map, XmlSerialName("mymap"), XmlMap("myentry", "mykey", "myvalue"))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }
        var actual = mutableMapOf<String, Int>()
        val deserializer = XmlDeserializer2(payload)
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index -> actual =
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
            SdkFieldDescriptor(SerialKind.Map, XmlSerialName("flatMap"), XmlMap(null, "key", "value", true))
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(containerFieldDescriptor)
        }
        var actual = mutableMapOf<String, Int>()
        val deserializer = XmlDeserializer2(payload)
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    containerFieldDescriptor.index -> actual =
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
            SdkFieldDescriptor(SerialKind.Map, XmlSerialName("Map"), XmlMap())
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(containerFieldDescriptor)
        }

        val deserializer = XmlDeserializer2(payload)
        var actual = mutableMapOf<String, Int>()
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    containerFieldDescriptor.index -> actual =
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
        val fieldDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"), XmlMap())
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }

        val deserializer = XmlDeserializer2(payload)
        var actual = mutableMapOf<String, Int?>()
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index -> actual =
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
        val fieldDescriptor = SdkFieldDescriptor(SerialKind.Map, XmlSerialName("values"), XmlMap())
        val objDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(fieldDescriptor)
        }

        val deserializer = XmlDeserializer2(payload)
        var actual = mutableMapOf<String, Int>()
        deserializer.deserializeStruct(objDescriptor) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    fieldDescriptor.index -> actual =
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
}
