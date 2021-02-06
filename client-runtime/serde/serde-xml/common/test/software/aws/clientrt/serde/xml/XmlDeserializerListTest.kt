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
        val list: List<Int>? = builder.list

        companion object {
            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
            fun dslBuilder(): DslBuilder = BuilderImpl()

            fun deserialize(deserializer: Deserializer, OBJ_DESCRIPTOR: SdkObjectDescriptor, ELEMENT_LIST_FIELD_DESCRIPTOR: SdkFieldDescriptor): ListDeserializer {
                val builder = dslBuilder()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            ELEMENT_LIST_FIELD_DESCRIPTOR.index -> builder.list = deserializer.deserializeList(ELEMENT_LIST_FIELD_DESCRIPTOR) {
                                val list = mutableListOf<Int>()
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
            var list: List<Int>?

            fun build(): ListDeserializer
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var list: List<Int>? = null

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

    class SparseListDeserializer private constructor(builder: BuilderImpl) {
        val list: List<Int?>? = builder.list

        companion object {
            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
            fun dslBuilder(): DslBuilder = BuilderImpl()

            fun deserialize(deserializer: Deserializer, OBJ_DESCRIPTOR: SdkObjectDescriptor, ELEMENT_LIST_FIELD_DESCRIPTOR: SdkFieldDescriptor): SparseListDeserializer {
                val builder = dslBuilder()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@while (true) {
                        when (findNextFieldIndex()) {
                            ELEMENT_LIST_FIELD_DESCRIPTOR.index ->
                                builder.list =
                                    deserializer.deserializeList(ELEMENT_LIST_FIELD_DESCRIPTOR) {
                                        val col0 = mutableListOf<Int?>()
                                        while (hasNextElement()) {
                                            val el0 = if (nextHasValue()) { deserializeInt() } else { deserializeNull() }
                                            col0.add(el0)
                                        }
                                        col0
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
            fun build(): SparseListDeserializer
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var list: List<Int?>?

            fun build(): SparseListDeserializer
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var list: List<Int?>? = null

            override fun build(): SparseListDeserializer = SparseListDeserializer(this)
        }
    }

    @Test
    fun itHandlesSparseLists() {
        val payload = """
            <object>
                <list>
                    <element>1</element>
                    <element></element>
                    <element>3</element>
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(), SparseValues)
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_LIST_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        val actual = SparseListDeserializer.deserialize(deserializer, OBJ_DESCRIPTOR, ELEMENT_LIST_FIELD_DESCRIPTOR).list
        val expected = listOf(1, null, 3)

        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesEmptyLists() {
        val payload = """
            <object>
                <list>                    
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
        val expected = emptyList<Unit>()

        assertEquals(expected, actual)
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
    fun itHandlesListOfObjectsWithMissingFields() {
        val payload = """
            <object>
               <list>
                   <payload>
                       <x>1</x>
                       <y>2</y>
                   </payload>
                   <payload>
                       <x></x>
                       <y>4</y>
                   </payload>
               </list>
           </object>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(elementName = "payload"))
        val objectDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(listWrapperFieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual: MutableList<SimpleStructClass>? = null
        deserializer.deserializeStruct(objectDescriptor) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    listWrapperFieldDescriptor.index -> actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
                        val list = mutableListOf<SimpleStructClass>()
                        while (hasNextElement()) {
                            list.add(SimpleStructClass.deserialize(deserializer))
                        }
                        return@deserializeList list
                    }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        assertEquals(2, actual!!.size)

        assertEquals(1, actual!![0].x)
        assertEquals(2, actual!![0].y)
        assertEquals(null, actual!![0].z)
        assertEquals(null, actual!![1].x)
        assertEquals(4, actual!![1].y)
        assertEquals(null, actual!![1].z)
    }

    @Test
    fun itHandlesListOfObjectsWithEmptyValues() {
        val payload = """
            <object>
               <list>
                   <payload>
                       <x>1</x>
                       <y>2</y>
                   </payload>
                   <payload />
               </list>
            </object>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList(elementName = "payload"))
        val objectDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(listWrapperFieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual: MutableList<SimpleStructClass>? = null
        deserializer.deserializeStruct(objectDescriptor) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    listWrapperFieldDescriptor.index -> actual = deserializer.deserializeList(listWrapperFieldDescriptor) {
                        val list = mutableListOf<SimpleStructClass>()
                        while (hasNextElement()) {
                            list.add(SimpleStructClass.deserialize(deserializer))
                        }
                        return@deserializeList list
                    }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        assertEquals(2, actual!!.size)
        assertEquals(1, actual!![0].x)
        assertEquals(2, actual!![0].y)
        assertEquals(null, actual!![1].x)
        assertEquals(null, actual!![1].y)
    }

    class NestedListDeserializer private constructor(builder: BuilderImpl) {
        val list: MutableList<List<String>>? = builder.list

        companion object {
            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
            fun dslBuilder(): DslBuilder = BuilderImpl()

            fun deserialize(deserializer: Deserializer, OBJ_DESCRIPTOR: SdkObjectDescriptor, ELEMENT_LIST_FIELD_DESCRIPTOR: SdkFieldDescriptor, NESTED_DESCRIPTOR: SdkFieldDescriptor): NestedListDeserializer {
                val builder = dslBuilder()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@while (true) {
                        when (findNextFieldIndex()) {
                            ELEMENT_LIST_FIELD_DESCRIPTOR.index ->
                                builder.list =
                                    deserializer.deserializeList(ELEMENT_LIST_FIELD_DESCRIPTOR) {
                                        val col0 = mutableListOf<List<String>>()
                                        while (hasNextElement()) {
                                            val el0 = deserializer.deserializeList(NESTED_DESCRIPTOR) {
                                                val col1 = mutableListOf<String>()
                                                while (hasNextElement()) {
                                                    val el1 = if (nextHasValue()) { deserializeString() } else { deserializeNull(); continue }
                                                    col1.add(el1)
                                                }
                                                col1
                                            }
                                            col0.add(el0)
                                        }
                                        col0
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
            fun build(): NestedListDeserializer
            // TODO - Java fill in Java builder
        }

        interface DslBuilder {
            var list: MutableList<List<String>>?

            fun build(): NestedListDeserializer
        }

        private class BuilderImpl : Builder, DslBuilder {
            override var list: MutableList<List<String>>? = null

            override fun build(): NestedListDeserializer = NestedListDeserializer(this)
        }
    }

    @Test
    fun itHandlesNestedLists() {
        val payload = """
            <object>
                <list>
                    <element>
                        <list>
                            <element>a</element>
                            <element>b</element>
                        </list>
                    </element>
                    <element>
                        <list>
                            <element>c</element>
                            <element>d</element>
                        </list>
                    </element>
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlList())
        val nestedListDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("nestedList"), XmlList())
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_LIST_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        val actual = NestedListDeserializer.deserialize(deserializer, OBJ_DESCRIPTOR, ELEMENT_LIST_FIELD_DESCRIPTOR, nestedListDescriptor).list
        val expected = listOf(listOf("a", "b"), listOf("c", "d"))

        actual.shouldContainExactly(expected)
    }
}
