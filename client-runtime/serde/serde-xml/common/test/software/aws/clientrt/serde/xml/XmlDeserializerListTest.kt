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
package software.aws.clientrt.serde.xml

import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import software.aws.clientrt.serde.SdkFieldDescriptor
import software.aws.clientrt.serde.SerialKind
import software.aws.clientrt.serde.deserializeList

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerListTest {

    @Test
    fun `it handles lists`() {
        val payload = """
            <list>
                <element>1</element>
                <element>2</element>
                <element>3</element>
            </list>
        """.encodeToByteArray()
        val listWrapperFieldDescriptor = SdkFieldDescriptor("list", SerialKind.List, 0, XmlList())
        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<Int>()
            while (hasNextElement()) {
                list.add(deserializeInt())
            }
            return@deserializeList list
        }
        val expected = listOf(1, 2, 3)
        actual.shouldContainExactly(expected)
    }

    @Test
    fun `it handles list of objects`() {
        val payload = """
               <list>
                   <payload>
                       <x>1</x>
                       <y>2</y>
                   </payload>
                   <payload>
                       <x>3</x>
                       <y>4</y>
                   </payload>
               </list>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor("list", SerialKind.List, 0, XmlList(elementName = "payload"))

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<SimpleStructClass>()
            while (hasNextElement()) {
                val obj = SimpleStructClass.deserialize(deserializer)
                list.add(obj)
            }
            return@deserializeList list
        }
        assertEquals(2, actual.size)
        assertEquals(1, actual[0].x)
        assertEquals(2, actual[0].y)
        assertEquals(3, actual[1].x)
        assertEquals(4, actual[1].y)
    }

    @Test
    fun `it handles list of objects with structs with empty values`() {
        val payload = """
               <list>
                   <payload>
                       <x>1</x>
                       <y></y>
                   </payload>
                   <payload>
                       <x></x>
                       <y>4</y>
                   </payload>
               </list>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor("list", SerialKind.List, 0, XmlList(elementName = "payload"))

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<SimpleStructClass>()
            while (hasNextElement()) {
                val obj = SimpleStructClass.deserialize(deserializer)
                list.add(obj)
            }
            return@deserializeList list
        }
        assertEquals(2, actual.size)
        assertEquals(1, actual[0].x)
        assertEquals(null, actual[0].y)
        assertEquals(null, actual[1].x)
        assertEquals(4, actual[1].y)
    }

    @Test
    fun `it handles list of objects with empty values`() {
        val payload = """
               <list>
                   <payload>
                       <x>1</x>
                       <y>2</y>
                   </payload>
                   <payload>
                   </payload>
               </list>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor("list", SerialKind.List, 0, XmlList(elementName = "payload"))

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<SimpleStructClass>()
            while (hasNextElement()) {
                val obj = SimpleStructClass.deserialize(deserializer)
                list.add(obj)
            }
            return@deserializeList list
        }
        assertEquals(2, actual.size)
        assertEquals(1, actual[0].x)
        assertEquals(2, actual[0].y)
        assertEquals(null, actual[1].x)
        assertEquals(null, actual[1].y)
    }

    @Test
    fun `it handles empty lists`() {
        val payload = """
               <list></list>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor("list", SerialKind.List, 0, XmlList(elementName = "payload"))

        val deserializer = XmlDeserializer(payload)
        val actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
            val list = mutableListOf<SimpleStructClass>()
            while (hasNextElement()) {
                val obj = SimpleStructClass.deserialize(deserializer)
                list.add(obj)
            }
            return@deserializeList list
        }
        assertEquals(0, actual.size)
    }
}
