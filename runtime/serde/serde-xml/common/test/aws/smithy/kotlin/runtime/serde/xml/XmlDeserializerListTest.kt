/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package aws.smithy.kotlin.runtime.serde.xml

import aws.smithy.kotlin.runtime.serde.*
import aws.smithy.kotlin.runtime.testing.runSuspendTest
import io.kotest.matchers.collections.shouldContainExactly
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerListTest {

    class ListDeserializer private constructor(builder: BuilderImpl) {
        val list: List<Int>? = builder.list

        companion object {
            operator fun invoke(block: DslBuilder.() -> Unit) = BuilderImpl().apply(block).build()
            fun dslBuilder(): DslBuilder = BuilderImpl()

            suspend fun deserialize(
                deserializer: Deserializer,
                OBJ_DESCRIPTOR: SdkObjectDescriptor,
                ELEMENT_LIST_FIELD_DESCRIPTOR: SdkFieldDescriptor
            ): ListDeserializer {
                val builder = dslBuilder()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            ELEMENT_LIST_FIELD_DESCRIPTOR.index ->
                                builder.list =
                                    deserializer.deserializeList(ELEMENT_LIST_FIELD_DESCRIPTOR) {
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
    fun itHandlesListSingleElement() = runSuspendTest {
        val payload = """
            <object>
                <list>
                    <member>1</member>
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_LIST_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        val actual = ListDeserializer.deserialize(deserializer, OBJ_DESCRIPTOR, ELEMENT_LIST_FIELD_DESCRIPTOR).list
        val expected = listOf(1)

        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesListMultipleElementsAndCustomMemberName() = runSuspendTest {
        val payload = """
            <object>
                <list>
                    <element>1</element>
                    <element>2</element>
                    <element>3</element>
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlCollectionName("element"))
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

            suspend fun deserialize(
                deserializer: Deserializer,
                OBJ_DESCRIPTOR: SdkObjectDescriptor,
                ELEMENT_LIST_FIELD_DESCRIPTOR: SdkFieldDescriptor
            ): SparseListDeserializer {
                val builder = dslBuilder()

                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            ELEMENT_LIST_FIELD_DESCRIPTOR.index ->
                                builder.list =
                                    deserializer.deserializeList(ELEMENT_LIST_FIELD_DESCRIPTOR) {
                                        val col0 = mutableListOf<Int?>()
                                        while (hasNextElement()) {
                                            val el0 = if (nextHasValue()) {
                                                deserializeInt()
                                            } else {
                                                deserializeNull()
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
    fun itHandlesSparseLists() = runSuspendTest {
        val payload = """
            <object>
                <list>
                    <member>1</member>
                    <member></member>
                    <member>3</member>
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), SparseValues)
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_LIST_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        val actual =
            SparseListDeserializer.deserialize(deserializer, OBJ_DESCRIPTOR, ELEMENT_LIST_FIELD_DESCRIPTOR).list
        val expected = listOf(1, null, 3)

        actual.shouldContainExactly(expected)
    }

    @Test
    fun itHandlesEmptyLists() = runSuspendTest {
        val payload = """
            <object>
                <list>                    
                </list>
            </object>
        """.encodeToByteArray()
        val ELEMENT_LIST_FIELD_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"))
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(ELEMENT_LIST_FIELD_DESCRIPTOR)
        }

        val deserializer = XmlDeserializer(payload)
        val actual = ListDeserializer.deserialize(deserializer, OBJ_DESCRIPTOR, ELEMENT_LIST_FIELD_DESCRIPTOR).list
        val expected = emptyList<Int>()

        assertEquals(expected, actual)
    }

    @Test
    fun itHandlesFlatLists() = runSuspendTest {
        val payload = """
            <object>
                <element>1</element>
                <element>2</element>
                <element>3</element>
            </object>
        """.encodeToByteArray()
        val elementFieldDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("element"), Flattened)
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
    fun itHandlesListOfObjectsWithMissingFields() = runSuspendTest {
        val payload = """
            <object>
               <list>
                   <payload>
                       <x>a</x>
                       <y>b</y>
                   </payload>
                   <payload>
                       <x></x>
                       <y>d</y>
                   </payload>
               </list>
           </object>
           """.encodeToByteArray()
        val listWrapperFieldDescriptor =
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlCollectionName(element = "payload"))
        val objectDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(listWrapperFieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual: MutableList<SimpleStructOfStringsClass>? = null
        deserializer.deserializeStruct(objectDescriptor) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    listWrapperFieldDescriptor.index ->
                        actual =
                            deserializer.deserializeList(listWrapperFieldDescriptor) {
                                val list = mutableListOf<SimpleStructOfStringsClass>()
                                while (hasNextElement()) {
                                    list.add(SimpleStructOfStringsClass.deserialize(deserializer))
                                }
                                return@deserializeList list
                            }
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        assertEquals(2, actual!!.size)

        assertEquals("a", actual!![0].x)
        assertEquals("b", actual!![0].y)
        assertEquals("", actual!![1].x)
        assertEquals("d", actual!![1].y)
    }

    @Test
    fun itHandlesListOfObjectsWithEmptyValues() = runSuspendTest {
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
            SdkFieldDescriptor(SerialKind.List, XmlSerialName("list"), XmlCollectionName(element = "payload"))
        val objectDescriptor = SdkObjectDescriptor.build {
            trait(XmlSerialName("object"))
            field(listWrapperFieldDescriptor)
        }

        val deserializer = XmlDeserializer(payload)
        var actual: MutableList<SimpleStructClass>? = null
        deserializer.deserializeStruct(objectDescriptor) {
            loop@ while (true) {
                when (findNextFieldIndex()) {
                    listWrapperFieldDescriptor.index ->
                        actual =
                            deserializer.deserializeList(listWrapperFieldDescriptor) {
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

    @Test
    fun itHandlesNestedLists() = runSuspendTest {
        val payload = """
            <NestedListResponse>
                <parentList>
                    <member>
                        <member>
                            <fooMember>a</fooMember>
                            <someInt>3</someInt>
                        </member>
                        <member>
                            <fooMember>a</fooMember>
                            <someInt>3</someInt>
                        </member>                        
                    </member>
                    <member>
                        <member>
                            <fooMember>b</fooMember>
                            <someInt>4</someInt>
                        </member>
                        <member>
                            <fooMember>c</fooMember>
                            <someInt>5</someInt>
                        </member>
                    </member>
                    <member>
                        <member>
                            <fooMember>d</fooMember>
                            <someInt>8</someInt>
                        </member>
                        <member>
                            <fooMember>e</fooMember>
                            <someInt>9</someInt>
                        </member>
                    </member>
                </parentList>
            </NestedListResponse>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val actual = NestedListOperationOperationDeserializer().deserialize(deserializer)

        assertTrue(actual.parentList?.size == 3)
    }

    @Test
    fun itHandlesListsOfStructs() = runSuspendTest {
        val payload = """
            <FooResponse>
                <parentList>
                    <member>
                        <fooMember>a</fooMember>
                        <someInt>3</someInt>
                    </member>
                    <member>
                        <fooMember>b</fooMember>
                        <someInt>4</someInt>
                    </member>
                    <member>
                        <fooMember>c</fooMember>
                        <someInt>6</someInt>
                    </member>
                </parentList>
            </FooResponse>
        """.encodeToByteArray()

        val listDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("parentList"))
        val deserializer = XmlDeserializer(payload)
        val actual = FooOperationDeserializer().deserialize(deserializer, listDescriptor)

        assertTrue(actual.parentList?.size == 3)
    }

    @Test
    fun itHandlesFlatListsOfStructs() = runSuspendTest {
        val payload = """
            <FooResponse>
                <flatList>
                    <fooMember>a</fooMember>
                    <someInt>3</someInt>
                </flatList>
                <flatList>
                    <fooMember>b</fooMember>
                    <someInt>4</someInt>
                </flatList>
                <flatList>
                    <fooMember>c</fooMember>
                    <someInt>6</someInt>
                </flatList>
            </FooResponse>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val listDescriptor = SdkFieldDescriptor(SerialKind.List, XmlSerialName("flatList"), Flattened)
        val actual = FooOperationDeserializer().deserialize(deserializer, listDescriptor)

        val parentList = assertNotNull(actual.parentList)
        assertEquals(3, parentList.size)
        assertEquals(parentList[0].fooMember, "a")
        assertEquals(parentList[0].someInt, 3)
        assertEquals(parentList[2].fooMember, "c")
        assertEquals(parentList[2].someInt, 6)
    }
}

internal class FooOperationDeserializer {

    suspend fun deserialize(
        deserializer: Deserializer,
        LIST_DESCRIPTOR: SdkFieldDescriptor
    ): FooResponse {
        val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("FooResponse"))
            field(LIST_DESCRIPTOR)
        }

        val builder = FooResponse.dslBuilder()

        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    LIST_DESCRIPTOR.index ->
                        builder.parentList =
                            deserializer.deserializeList(LIST_DESCRIPTOR) {
                                val col0 = mutableListOf<PayloadStruct>()
                                while (hasNextElement()) {
                                    val el0 = if (nextHasValue()) { PayloadStructDocumentDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
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

internal class PayloadStructDocumentDeserializer {

    companion object {
        private val FOOMEMBER_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("fooMember"))
        private val SOMEINT_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("someInt"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            field(FOOMEMBER_DESCRIPTOR)
            field(SOMEINT_DESCRIPTOR)
        }
    }

    suspend fun deserialize(deserializer: Deserializer): PayloadStruct {
        val builder = PayloadStruct.dslBuilder()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    FOOMEMBER_DESCRIPTOR.index -> builder.fooMember = deserializeString()
                    SOMEINT_DESCRIPTOR.index -> builder.someInt = deserializeInt()
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}

class FooResponse private constructor(builder: BuilderImpl) {
    val parentList: List<PayloadStruct>? = builder.parentList

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): FooResponse = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("FooResponse(")
        append("parentList=$parentList)")
    }

    override fun hashCode(): kotlin.Int {
        var result = parentList?.hashCode() ?: 0
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as FooResponse

        if (parentList != other.parentList) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): FooResponse = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): FooResponse
        fun parentList(parentList: List<PayloadStruct>): Builder
    }

    interface DslBuilder {
        var parentList: List<PayloadStruct>?

        fun build(): FooResponse
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var parentList: List<PayloadStruct>? = null

        constructor(x: FooResponse) : this() {
            this.parentList = x.parentList
        }

        override fun build(): FooResponse = FooResponse(this)
        override fun parentList(parentList: List<PayloadStruct>): Builder = apply { this.parentList = parentList }
    }
}

class PayloadStruct private constructor(builder: BuilderImpl) {
    val fooMember: String? = builder.fooMember
    val someInt: Int? = builder.someInt

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): PayloadStruct = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("PayloadStruct(")
        append("fooMember=$fooMember,")
        append("someInt=$someInt)")
    }

    override fun hashCode(): kotlin.Int {
        var result = fooMember?.hashCode() ?: 0
        result = 31 * result + (someInt ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as PayloadStruct

        if (fooMember != other.fooMember) return false
        if (someInt != other.someInt) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): PayloadStruct = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): PayloadStruct
        fun fooMember(fooMember: String): Builder
        fun someInt(someInt: Int): Builder
    }

    interface DslBuilder {
        var fooMember: String?
        var someInt: Int?

        fun build(): PayloadStruct
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var fooMember: String? = null
        override var someInt: Int? = null

        constructor(x: PayloadStruct) : this() {
            this.fooMember = x.fooMember
            this.someInt = x.someInt
        }

        override fun build(): PayloadStruct = PayloadStruct(this)
        override fun fooMember(fooMember: String): Builder = apply { this.fooMember = fooMember }
        override fun someInt(someInt: Int): Builder = apply { this.someInt = someInt }
    }
}

class NestedListResponse private constructor(builder: BuilderImpl) {
    val parentList: List<List<PayloadStruct>>? = builder.parentList

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): NestedListResponse = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("NestedListResponse(")
        append("parentList=$parentList)")
    }

    override fun hashCode(): kotlin.Int {
        var result = parentList?.hashCode() ?: 0
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as NestedListResponse

        if (parentList != other.parentList) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): NestedListResponse = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): NestedListResponse
        fun parentList(parentList: List<List<PayloadStruct>>): Builder
    }

    interface DslBuilder {
        var parentList: List<List<PayloadStruct>>?

        fun build(): NestedListResponse
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var parentList: List<List<PayloadStruct>>? = null

        constructor(x: NestedListResponse) : this() {
            this.parentList = x.parentList
        }

        override fun build(): NestedListResponse = NestedListResponse(this)
        override fun parentList(parentList: List<List<PayloadStruct>>): Builder = apply { this.parentList = parentList }
    }
}

internal class NestedListOperationOperationDeserializer {

    companion object {
        private val PARENTLIST_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("parentList"))
        private val PARENTLIST_C0_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, XmlSerialName("parentListC0"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("NestedListResponse"))
            field(PARENTLIST_DESCRIPTOR)
        }
    }

    suspend fun deserialize(deserializer: Deserializer): NestedListResponse {
        val builder = NestedListResponse.dslBuilder()

        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    PARENTLIST_DESCRIPTOR.index ->
                        builder.parentList =
                            deserializer.deserializeList(PARENTLIST_DESCRIPTOR) {
                                val col0 = mutableListOf<List<PayloadStruct>>()
                                while (hasNextElement()) {
                                    val el0 = deserializer.deserializeList(PARENTLIST_C0_DESCRIPTOR) {
                                        val col1 = mutableListOf<PayloadStruct>()
                                        while (hasNextElement()) {
                                            val el1 = if (nextHasValue()) { PayloadStructDocumentDeserializer().deserialize(deserializer) } else { deserializeNull(); continue }
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
