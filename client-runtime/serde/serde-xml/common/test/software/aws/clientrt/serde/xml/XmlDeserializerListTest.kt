/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import io.kotest.matchers.collections.shouldContainExactly
import software.aws.clientrt.serde.*
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerListTest {

    class ListDeserializer private constructor(builder: BuilderImpl) {
        val list: List<Int?>? = builder.list

        companion object {
            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
            fun dslBuilder(): DslBuilder = BuilderImpl()

            fun deserialize(deserializer: Deserializer, OBJ_DESCRIPTOR: SdkObjectDescriptor, ELEMENT_LIST_FIELD_DESCRIPTOR: SdkFieldDescriptor): ListDeserializer {
                val builder = dslBuilder()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            ELEMENT_LIST_FIELD_DESCRIPTOR.index -> builder.list = deserializer.deserializeList(ELEMENT_LIST_FIELD_DESCRIPTOR) {
                                val list = mutableListOf<Int?>()
                                while (hasNextElement()) {
                                    list.add(deserializeInt())
                                }
                                return@deserializeList list
                            }
                            null -> break@loop
                            else -> skipValue()
                        }
                    }
                }

                return builder.build()
            }
        }

        interface Builder {
            fun build(): ListDeserializer
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var list: List<Int?>?

            fun build(): ListDeserializer
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var list: List<Int?>? = null

            override fun build(): ListDeserializer = ListDeserializer(this)
        }
    }

    @Test
    fun itHandlesLists() {
        val payload = """
            <object>
                <list>
                    <element>1</element>
                    <element>2</element>
                    <element>3</element>
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList())
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_LIST_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        val actual = ListDeserializer.deserialize(deserializer, OBJ_DESCRIPTOR, ELEMENT_LIST_FIELD_DESCRIPTOR).list
        val expected = listOf(1, 2, 3)

        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesFlatLists() {
        val payload = """
            <object>
                <element>1</element>
                <element>2</element>
                <element>3</element>
            </object>
        """.encodeToByteArray()
        val elementFieldDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("element"), XmlList(flattened = true))
        val objectDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(elementFieldDescriptor)
        }
        val deserializer = XmlDeserializer(payload)
        val actual = ListDeserializer.deserialize(deserializer, objectDescriptor, elementFieldDescriptor).list
        val expected = listOf(1, 2, 3)

        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesListOfObjects() {
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
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(elementName = "payload"))

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
    fun itHandlesListOfObjectsWithStructsWithEmptyValues() {
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
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(elementName = "payload"))

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
    fun itHandlesListOfObjectsWithEmptyValues() {
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
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(elementName = "payload"))

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
    fun itHandlesEmptyLists() {
        val payload = """
               <list></list>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(elementName = "payload"))

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
