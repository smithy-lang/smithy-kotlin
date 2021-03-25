/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.aws.clientrt.serde.xml

import software.aws.clientrt.serde.*
import software.aws.clientrt.testing.runSuspendTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class XmlDeserializerStructTest {
    @Test
    fun `it handles basic structs with attribs`() = runSuspendTest {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0.
                 -->
                
               <payload x="1" y="2" />
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = StructWithAttribsClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    @Test
    fun `it handles basic structs with multi attribs and text`() = runSuspendTest {
        val payload = """
               <?xml version="1.0" encoding="UTF-8"?>
               <!--
                 ~ Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
                 ~ SPDX-License-Identifier: Apache-2.0.
                 -->
                
               <payload xval="1" yval="2">
                    <x>nodeval</x>
               </payload>
        """.trimIndent().encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = StructWithMultiAttribsAndTextValClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
        assertEquals("nodeval", bst.txt)
    }

    @Test
    fun itHandlesBasicStructsWithAttribsAndText() = runSuspendTest {
        val payload = """
            <payload xa="1" ya="2">
                <x>x1</x>
                <y/>
                <z>true</z>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = BasicAttribTextStructTest.deserialize(deserializer)

        assertEquals(1, bst.xa)
        assertEquals("x1", bst.xt)
        assertEquals(2, bst.y)
        assertEquals(0, bst.unknownFieldCount)
    }

    class BasicAttribTextStructTest {
        var xa: Int? = null
        var xt: String? = null
        var y: Int? = null
        var z: Boolean? = null
        var unknownFieldCount: Int = 0

        companion object {
            val X_ATTRIB_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("xa"), XmlAttribute)
            val X_VALUE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("ya"), XmlAttribute)
            val Z_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Boolean, XmlSerialName("z"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("payload"))
                field(X_ATTRIB_DESCRIPTOR)
                field(X_VALUE_DESCRIPTOR)
                field(Y_DESCRIPTOR)
                field(Z_DESCRIPTOR)
            }

            suspend fun deserialize(deserializer: Deserializer): BasicAttribTextStructTest {
                val result = BasicAttribTextStructTest()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_ATTRIB_DESCRIPTOR.index -> result.xa = deserializeInt()
                            X_VALUE_DESCRIPTOR.index -> result.xt = deserializeString()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
                            Z_DESCRIPTOR.index -> result.z = deserializeBoolean()
                            null -> break@loop
                            Deserializer.FieldIterator.UNKNOWN_FIELD -> {
                                result.unknownFieldCount++
                                skipValue()
                            }
                            else -> throw XmlGenerationException(IllegalStateException("unexpected field in BasicStructTest deserializer"))
                        }
                    }
                }
                return result
            }
        }
    }

    @Test
    fun itHandlesBasicStructs() = runSuspendTest {
        val payload = """
            <payload>
                <x>1</x>
                <y>2</y>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
    }

    @Test
    fun itHandlesBasicStructsWithNullValues() = runSuspendTest {
        val payload1 = """
            <payload>
                <x>a</x>
                <y></y>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload1)
        val bst = SimpleStructOfStringsClass.deserialize(deserializer)

        assertEquals("a", bst.x)
        assertEquals("", bst.y)

        val payload2 = """
            <payload>
                <x></x>
                <y>2</y>
            </payload>
        """.encodeToByteArray()

        val deserializer2 = XmlDeserializer(payload2)
        val bst2 = SimpleStructOfStringsClass.deserialize(deserializer2)

        assertEquals("", bst2.x)
        assertEquals("2", bst2.y)
    }

    @Test
    fun itEnumeratesUnknownStructFields() = runSuspendTest {
        val payload = """
               <payload z="strval">
                   <x>1</x>
                   <y>2</y>
               </payload>
           """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(2, bst.y)
        assertEquals("strval", bst.z)
    }

    @Test
    fun itHandlesNestedXmlStructures() = runSuspendTest {
        val payload = """
            <RecursiveShapesInputOutput>
                <nested>
                    <foo>Foo1</foo>
                    <nested>
                        <bar>Bar1</bar>
                        <recursiveMember>
                            <foo>Foo2</foo>
                            <nested>
                                <bar>Bar2</bar>
                            </nested>
                        </recursiveMember>
                    </nested>
                </nested>
            </RecursiveShapesInputOutput>
           """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val bst = RecursiveShapesOperationDeserializer().deserialize(deserializer)

        println(bst.nested?.nested)
    }
}

internal class RecursiveShapesOperationDeserializer {

    companion object {
        private val NESTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            trait(XmlSerialName("RecursiveShapesInputOutput"))
            field(NESTED_DESCRIPTOR)
        }
    }

    suspend fun deserialize(deserializer: Deserializer): RecursiveShapesInputOutput {
        val builder = RecursiveShapesInputOutput.dslBuilder()

        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    NESTED_DESCRIPTOR.index -> builder.nested = RecursiveShapesInputOutputNested1DocumentDeserializer().deserialize(deserializer)
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }

        return builder.build()
    }
}

internal class RecursiveShapesInputOutputNested1DocumentDeserializer {

    companion object {
        private val FOO_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("foo"))
        private val NESTED_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("nested"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            field(FOO_DESCRIPTOR)
            field(NESTED_DESCRIPTOR)
        }
    }

    suspend fun deserialize(deserializer: Deserializer): RecursiveShapesInputOutputNested1 {
        val builder = RecursiveShapesInputOutputNested1.dslBuilder()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    FOO_DESCRIPTOR.index -> builder.foo = deserializeString()
                    NESTED_DESCRIPTOR.index -> builder.nested = RecursiveShapesInputOutputNested2DocumentDeserializer().deserialize(deserializer)
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}

internal class RecursiveShapesInputOutputNested2DocumentDeserializer {

    companion object {
        private val BAR_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("bar"))
        private val RECURSIVEMEMBER_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Struct, XmlSerialName("recursiveMember"))
        private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
            field(BAR_DESCRIPTOR)
            field(RECURSIVEMEMBER_DESCRIPTOR)
        }
    }

    suspend fun deserialize(deserializer: Deserializer): RecursiveShapesInputOutputNested2 {
        val builder = RecursiveShapesInputOutputNested2.dslBuilder()
        deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
            loop@while (true) {
                when (findNextFieldIndex()) {
                    BAR_DESCRIPTOR.index -> builder.bar = deserializeString()
                    RECURSIVEMEMBER_DESCRIPTOR.index -> builder.recursiveMember = RecursiveShapesInputOutputNested1DocumentDeserializer().deserialize(deserializer)
                    null -> break@loop
                    else -> skipValue()
                }
            }
        }
        return builder.build()
    }
}

class RecursiveShapesInputOutput private constructor(builder: BuilderImpl) {
    val nested: RecursiveShapesInputOutputNested1? = builder.nested

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): RecursiveShapesInputOutput = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("RecursiveShapesInputOutput(")
        append("nested=$nested)")
    }

    override fun hashCode(): kotlin.Int {
        var result = nested?.hashCode() ?: 0
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as RecursiveShapesInputOutput

        if (nested != other.nested) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): RecursiveShapesInputOutput = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): RecursiveShapesInputOutput
        fun nested(nested: RecursiveShapesInputOutputNested1): Builder
    }

    interface DslBuilder {
        var nested: RecursiveShapesInputOutputNested1?

        fun build(): RecursiveShapesInputOutput
        fun nested(block: RecursiveShapesInputOutputNested1.DslBuilder.() -> kotlin.Unit) {
            this.nested = RecursiveShapesInputOutputNested1.invoke(block)
        }
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var nested: RecursiveShapesInputOutputNested1? = null

        constructor(x: RecursiveShapesInputOutput) : this() {
            this.nested = x.nested
        }

        override fun build(): RecursiveShapesInputOutput = RecursiveShapesInputOutput(this)
        override fun nested(nested: RecursiveShapesInputOutputNested1): Builder = apply { this.nested = nested }
    }
}

class RecursiveShapesInputOutputNested1 private constructor(builder: BuilderImpl) {
    val foo: String? = builder.foo
    val nested: RecursiveShapesInputOutputNested2? = builder.nested

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): RecursiveShapesInputOutputNested1 = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("RecursiveShapesInputOutputNested1(")
        append("foo=$foo,")
        append("nested=$nested)")
    }

    override fun hashCode(): kotlin.Int {
        var result = foo?.hashCode() ?: 0
        result = 31 * result + (nested?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as RecursiveShapesInputOutputNested1

        if (foo != other.foo) return false
        if (nested != other.nested) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): RecursiveShapesInputOutputNested1 = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): RecursiveShapesInputOutputNested1
        fun foo(foo: String): Builder
        fun nested(nested: RecursiveShapesInputOutputNested2): Builder
    }

    interface DslBuilder {
        var foo: String?
        var nested: RecursiveShapesInputOutputNested2?

        fun build(): RecursiveShapesInputOutputNested1
        fun nested(block: RecursiveShapesInputOutputNested2.DslBuilder.() -> kotlin.Unit) {
            this.nested = RecursiveShapesInputOutputNested2.invoke(block)
        }
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var foo: String? = null
        override var nested: RecursiveShapesInputOutputNested2? = null

        constructor(x: RecursiveShapesInputOutputNested1) : this() {
            this.foo = x.foo
            this.nested = x.nested
        }

        override fun build(): RecursiveShapesInputOutputNested1 = RecursiveShapesInputOutputNested1(this)
        override fun foo(foo: String): Builder = apply { this.foo = foo }
        override fun nested(nested: RecursiveShapesInputOutputNested2): Builder = apply { this.nested = nested }
    }
}

class RecursiveShapesInputOutputNested2 private constructor(builder: BuilderImpl) {
    val bar: String? = builder.bar
    val recursiveMember: RecursiveShapesInputOutputNested1? = builder.recursiveMember

    companion object {
        fun builder(): Builder = BuilderImpl()

        fun dslBuilder(): DslBuilder = BuilderImpl()

        operator fun invoke(block: DslBuilder.() -> kotlin.Unit): RecursiveShapesInputOutputNested2 = BuilderImpl().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("RecursiveShapesInputOutputNested2(")
        append("bar=$bar,")
        append("recursiveMember=$recursiveMember)")
    }

    override fun hashCode(): kotlin.Int {
        var result = bar?.hashCode() ?: 0
        result = 31 * result + (recursiveMember?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true

        other as RecursiveShapesInputOutputNested2

        if (bar != other.bar) return false
        if (recursiveMember != other.recursiveMember) return false

        return true
    }

    fun copy(block: DslBuilder.() -> kotlin.Unit = {}): RecursiveShapesInputOutputNested2 = BuilderImpl(this).apply(block).build()

    interface Builder {
        fun build(): RecursiveShapesInputOutputNested2
        fun bar(bar: String): Builder
        fun recursiveMember(recursiveMember: RecursiveShapesInputOutputNested1): Builder
    }

    interface DslBuilder {
        var bar: String?
        var recursiveMember: RecursiveShapesInputOutputNested1?

        fun build(): RecursiveShapesInputOutputNested2
        fun recursiveMember(block: RecursiveShapesInputOutputNested1.DslBuilder.() -> kotlin.Unit) {
            this.recursiveMember = RecursiveShapesInputOutputNested1.invoke(block)
        }
    }

    private class BuilderImpl() : Builder, DslBuilder {
        override var bar: String? = null
        override var recursiveMember: RecursiveShapesInputOutputNested1? = null

        constructor(x: RecursiveShapesInputOutputNested2) : this() {
            this.bar = x.bar
            this.recursiveMember = x.recursiveMember
        }

        override fun build(): RecursiveShapesInputOutputNested2 = RecursiveShapesInputOutputNested2(this)
        override fun bar(bar: String): Builder = apply { this.bar = bar }
        override fun recursiveMember(recursiveMember: RecursiveShapesInputOutputNested1): Builder = apply { this.recursiveMember = recursiveMember }
    }
}
