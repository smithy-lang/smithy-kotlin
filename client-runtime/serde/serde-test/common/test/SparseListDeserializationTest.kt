/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.Deserializer
import software.aws.clientrt.serde.json.JsonSerdeProvider
import software.aws.clientrt.serde.json.JsonSerialName
import software.aws.clientrt.serde.xml.XmlSerdeProvider
import software.aws.clientrt.serde.xml.XmlSerialName
import software.aws.clientrt.testing.runSuspendTest
import kotlin.jvm.JvmStatic
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SparseListDeserializationTest {

    class GetFooOutput private constructor(builder: BuilderImpl) {
        val sparseStructList: List<Greeting?>? = builder.sparseStructList

        companion object {
            @JvmStatic
            fun builder(): Builder = BuilderImpl()

            fun dslBuilder(): DslBuilder = BuilderImpl()

            operator fun invoke(block: DslBuilder.() -> Unit): GetFooOutput = BuilderImpl().apply(block).build()
        }

        override fun toString() = buildString {
            append("GetFooOutput(")
            append("sparseStructList=$sparseStructList)")
        }

        override fun hashCode(): Int {
            var result = sparseStructList?.hashCode() ?: 0
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as GetFooOutput

            if (sparseStructList != other.sparseStructList) return false

            return true
        }

        fun copy(block: DslBuilder.() -> Unit = {}): GetFooOutput = BuilderImpl(this).apply(block).build()

        interface Builder {
            fun build(): GetFooOutput
            fun sparseStructList(sparseStructList: List<Greeting?>): Builder
        }

        interface DslBuilder {
            var sparseStructList: List<Greeting?>?

            fun build(): GetFooOutput
        }

        private class BuilderImpl() : Builder, DslBuilder {
            override var sparseStructList: List<Greeting?>? = null

            constructor(x: GetFooOutput) : this() {
                this.sparseStructList = x.sparseStructList
            }

            override fun build(): GetFooOutput = GetFooOutput(this)
            override fun sparseStructList(sparseStructList: List<Greeting?>): Builder = apply { this.sparseStructList = sparseStructList }
        }
    }

    class Greeting private constructor(builder: BuilderImpl) {
        val saying: String? = builder.saying

        companion object {
            @JvmStatic
            fun builder(): Builder = BuilderImpl()

            fun dslBuilder(): DslBuilder = BuilderImpl()

            operator fun invoke(block: DslBuilder.() -> Unit): Greeting = BuilderImpl().apply(block).build()
        }

        override fun toString() = buildString {
            append("Greeting(")
            append("saying=$saying)")
        }

        override fun hashCode(): Int {
            var result = saying?.hashCode() ?: 0
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as Greeting

            if (saying != other.saying) return false

            return true
        }

        fun copy(block: DslBuilder.() -> Unit = {}): Greeting = BuilderImpl(this).apply(block).build()

        interface Builder {
            fun build(): Greeting
            fun saying(saying: String): Builder
        }

        interface DslBuilder {
            var saying: String?

            fun build(): Greeting
        }

        private class BuilderImpl() : Builder, DslBuilder {
            override var saying: String? = null

            constructor(x: Greeting) : this() {
                this.saying = x.saying
            }

            override fun build(): Greeting = Greeting(this)
            override fun saying(saying: String): Builder = apply { this.saying = saying }
        }
    }

    class GetFooDeserializer {

        companion object {
            private val SPARSESTRUCTLIST_DESCRIPTOR = SdkFieldDescriptor(SerialKind.List, "sparseStructList".toSerialNames())
            private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
                trait(XmlSerialName("GetFoo"))
                field(SPARSESTRUCTLIST_DESCRIPTOR)
            }
        }

        suspend fun deserialize(deserializer: Deserializer): GetFooOutput {
            val builder = GetFooOutput.dslBuilder()

            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        SPARSESTRUCTLIST_DESCRIPTOR.index ->
                            builder.sparseStructList =
                                deserializer.deserializeList(SPARSESTRUCTLIST_DESCRIPTOR) {
                                    val col0 = mutableListOf<Greeting?>()
                                    while (hasNextElement()) {
                                        val el0 = if (nextHasValue()) { GreetingDeserializer().deserialize(deserializer) } else { deserializeNull() }
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

    class GreetingDeserializer {

        companion object {
            private val SAYING_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, "saying".toSerialNames())
            private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
                trait(XmlSerialName("Greeting"))
                trait(JsonSerialName("Greeting"))
                field(SAYING_DESCRIPTOR)
            }
        }

        suspend fun deserialize(deserializer: Deserializer): Greeting {
            val builder = Greeting.dslBuilder()
            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        SAYING_DESCRIPTOR.index -> builder.saying = deserializeString()
                        null -> break@loop
                        else -> skipValue()
                    }
                }
            }
            return builder.build()
        }
    }

    companion object {
        private val xmlSerdeProvider = XmlSerdeProvider()
        private val jsonSerdeProvider = JsonSerdeProvider()
    }

    @Test
    fun itDeserializesAnEmptyDocumentIntoAnEmptyStruct() = runSuspendTest {
        val jsonPayload = "{}".encodeToByteArray()
        val xmlPayload = "<GetFoo />".encodeToByteArray()

        for (deserializer in listOf(jsonSerdeProvider.deserializer(jsonPayload), xmlSerdeProvider.deserializer(xmlPayload))) {
            val struct = GetFooDeserializer().deserialize(deserializer)

            assertNotNull(struct)
            assertNull(struct.sparseStructList)
        }
    }

    @Test
    fun itDeserializesAnEmptyMapIntoAnStructWithEmptyMap() = runSuspendTest {
        val jsonPayload = """
            {
                "sparseStructList": []
            }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <GetFoo>
                <sparseStructList />
            </GetFoo>
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(jsonSerdeProvider.deserializer(jsonPayload), xmlSerdeProvider.deserializer(xmlPayload))) {
            val struct = GetFooDeserializer().deserialize(deserializer)

            assertNotNull(struct)
            assertNotNull(struct.sparseStructList)
            assertTrue(struct.sparseStructList.isEmpty())
        }
    }

    @Test
    fun itDeserializesAMapWithNullValuesIntoAnStructWithMapContainingKeysWithNullValues() = runSuspendTest {
        val jsonPayload = """
            {
                "sparseStructList": [
                    {"saying": "boo"},
                    null,
                    {"saying": "hoo"}
                ]
            }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <GetFoo>
                <sparseStructList>
                    <member>
                        <saying>boo</saying>    
                    </member>
                    <member />                            
                    <member>                        
                        <saying>hoo</saying>                        
                    </member>
                </sparseStructList>
            </GetFoo>
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(jsonSerdeProvider.deserializer(jsonPayload), xmlSerdeProvider.deserializer(xmlPayload))) {
            val struct = GetFooDeserializer().deserialize(deserializer)

            assertNotNull(struct)
            assertNotNull(struct.sparseStructList)
            assertTrue(struct.sparseStructList.size == 3)
            assertNotNull(struct.sparseStructList[0])
            assertNull(struct.sparseStructList[1])
            assertNotNull(struct.sparseStructList[2])
        }
    }
}
