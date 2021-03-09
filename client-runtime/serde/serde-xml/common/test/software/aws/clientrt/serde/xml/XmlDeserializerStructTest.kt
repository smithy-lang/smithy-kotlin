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
                
               <payload>
                    <x value="1" />
                    <y value="2" />
               </payload>
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
                
               <payload>
                    <x xval="1" yval="2">nodeval</x>
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
            <payload>
                <x value="1">x1</x>
                <y value="2" />
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
            val X_ATTRIB_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"), XmlAttribute("value"))
            val X_VALUE_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("x"))
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, XmlSerialName("y"), XmlAttribute("value"))
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
                <x>1</x>
                <y></y>
            </payload>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload1)
        val bst = SimpleStructClass.deserialize(deserializer)

        assertEquals(1, bst.x)
        assertEquals(null, bst.y)

        val payload2 = """
            <payload>
                <x></x>
                <y>2</y>
            </payload>
        """.encodeToByteArray()

        val deserializer2 = XmlDeserializer(payload2)
        val bst2 = SimpleStructClass.deserialize(deserializer2)

        assertEquals(null, bst2.x)
        assertEquals(2, bst2.y)
    }

    @Test
    fun itEnumeratesUnknownStructFields() = runSuspendTest {
        val payload = """
               <payload>
                   <x>1</x>
                   <z attribval="strval">unknown field</z>
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
    fun itDeserializesFieldsWithEscapedCharacters() = runSuspendTest {
        val payload = """
            <CityInfo>
                <country>USA</country>
                <name>&lt;Lake Forest&gt;</name>
            </CityInfo>
        """.encodeToByteArray()

        val deserializer = XmlDeserializer(payload)
        val cityInfo = CityInfoDocumentDeserializer().deserialize(deserializer)

        assertEquals("USA", cityInfo.country)
        assertEquals("<Lake Forest>", cityInfo.name)
    }

    class CityInfoDocumentDeserializer {

        companion object {
            private val COUNTRY_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("country"))
            private val NAME_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, XmlSerialName("name"))
            private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("CityInfo"))
                field(COUNTRY_DESCRIPTOR)
                field(NAME_DESCRIPTOR)
            }
        }

        suspend fun deserialize(deserializer: Deserializer): CityInfo {
            val builder = CityInfo.dslBuilder()
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        COUNTRY_DESCRIPTOR.index -> builder.country = deserializeString()
                        NAME_DESCRIPTOR.index -> builder.name = deserializeString()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
            return builder.build()
        }
    }

    class CityInfo private constructor(builder: BuilderImpl) {
        val country: String? = builder.country
        val name: String? = builder.name

        companion object {
            fun builder(): Builder = BuilderImpl()

            fun dslBuilder(): DslBuilder = BuilderImpl()

            operator fun invoke(block: DslBuilder.() -> kotlin.Unit): CityInfo = BuilderImpl().apply(block).build()
        }

        override fun toString(): kotlin.String = buildString {
            append("CityInfo(")
            append("country=$country,")
            append("name=$name)")
        }

        override fun hashCode(): kotlin.Int {
            var result = country?.hashCode() ?: 0
            result = 31 * result + (name?.hashCode() ?: 0)
            return result
        }

        override fun equals(other: kotlin.Any?): kotlin.Boolean {
            if (this === other) return true

            other as CityInfo

            if (country != other.country) return false
            if (name != other.name) return false

            return true
        }

        fun copy(block: DslBuilder.() -> kotlin.Unit = {}): CityInfo = BuilderImpl(this).apply(block).build()

        interface Builder {
            fun build(): CityInfo
            fun country(country: String): Builder
            fun name(name: String): Builder
        }

        interface DslBuilder {
            var country: String?
            var name: String?

            fun build(): CityInfo
        }

        private class BuilderImpl() : Builder, DslBuilder {
            override var country: String? = null
            override var name: String? = null

            constructor(x: CityInfo) : this() {
                this.country = x.country
                this.name = x.name
            }

            override fun build(): CityInfo = CityInfo(this)
            override fun country(country: String): Builder = apply { this.country = country }
            override fun name(name: String): Builder = apply { this.name = name }
        }
    }
}
