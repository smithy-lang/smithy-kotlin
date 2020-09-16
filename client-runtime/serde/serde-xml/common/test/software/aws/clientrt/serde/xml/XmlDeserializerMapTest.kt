/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import io.kotest.matchers.maps.shouldContainExactly
import kotlin.test.Ignore
import kotlin.test.Test
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SerialKind
import software.aws.clientrt.serde.deserializeMap

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerMapTest {

    @Test
    fun `it handles maps with default node names`() {
        val payload = """
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
        """.encodeToByteArray()
        val fieldDescriptor = SdkFieldDescriptor("values", SerialKind.Map, 0, XmlMap())

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(fieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                val key = key()
                val value = deserializeInt()

                map[key] = value
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to 2)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun `it handles maps with custom node names`() {
        val payload = """
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
        """.encodeToByteArray()
        val fieldDescriptor =
            SdkFieldDescriptor("mymap", SerialKind.Map, 0, XmlMap("myentry", "mykey", "myvalue"))
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(fieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                val key = key()
                val value = deserializeInt()

                map[key] = value
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to 2)
        actual.shouldContainExactly(expected)
    }

    // https://awslabs.github.io/smithy/1.0/spec/core/xml-traits.html#flattened-map-serialization
    @Test
    fun `it handles flat maps`() {
        val payload = """
            <Bar>
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
            </Bar>
        """.encodeToByteArray()
        val containerFieldDescriptor =
            SdkFieldDescriptor("Bar", SerialKind.Map, 0, XmlMap("flatMap", "key", "value", true))
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(containerFieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializer.deserializeInt()
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to 2, "key3" to 3)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun `it handles empty maps`() {
        val payload = """
            <Map></Map>
        """.encodeToByteArray()
        val containerFieldDescriptor =
            SdkFieldDescriptor("Map", SerialKind.Map, 0, XmlMap())
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(containerFieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                map[key()] = deserializer.deserializeInt()
            }
            return@deserializeMap map
        }
        val expected = emptyMap<String, Int>()
        actual.shouldContainExactly(expected)
    }

    @Test
    @Ignore
    // TODO: This test fails because the primitive deserializer interface must return a value.  In order to allow nulls
    //       we must revise the interface or come up w/ another strategy.
    fun `it handles maps with entries with empty values`() {
        val payload = """
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
        """.encodeToByteArray()
        val fieldDescriptor = SdkFieldDescriptor("values", SerialKind.Map, 0, XmlMap())

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeMap(fieldDescriptor) {
            val map = mutableMapOf<String, Int>()
            while (hasNextEntry()) {
                val key = key()
                val value = deserializeInt()

                map[key] = value
            }
            return@deserializeMap map
        }
        val expected = mapOf("key1" to 1, "key2" to null)
        actual.shouldContainExactly(expected)
    }
}
