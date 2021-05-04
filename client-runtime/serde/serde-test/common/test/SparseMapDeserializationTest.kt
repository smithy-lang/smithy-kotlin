/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

import io.kotest.matchers.maps.shouldContainKeys
import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.json.JsonSerdeProvider
import software.aws.clientrt.serde.xml.XmlSerdeProvider
import software.aws.clientrt.serde.xml.XmlSerialName
import software.aws.clientrt.testing.runSuspendTest
import kotlin.jvm.JvmStatic
import kotlin.test.*

/**
 * This test uses codegen snapshots generated from the following model:
 * namespace com.test
 *
 * use aws.protocols#restJson1
 *
 * @restJson1
 * service Example {
 *      version: "1.0.0",
 *      operations: [GetFoo]
 * }
 *
 * @http(method: "POST", uri: "/input/list")
 *      operation GetFoo {
 *      output: GetFooOutput
 * }
 *
 * structure Greeting {
 *      saying: String
 * }
 *
 * @sparse
 * map SparseStructMap {
 *      key: String,
 *      value: Greeting
 * }
 *
 * structure GetFooOutput {
 *      sparseStructMap: SparseStructMap
 * }
 */
// TODO - this test can be moved into integration test and the test model can be applied directly
//        to generated deserializers rather than copied and adapted in-line.
//        https://www.pivotaltracker.com/story/show/176162626
class SparseMapDeserializationTest {

    class GetFooOutput private constructor(builder: BuilderImpl) {
        val sparseStructMap: Map<String, Greeting?>? = builder.sparseStructMap

        companion object {
            @JvmStatic
            fun builder(): Builder = BuilderImpl()

            fun dslBuilder(): DslBuilder = BuilderImpl()

            operator fun invoke(block: DslBuilder.() -> Unit): GetFooOutput = BuilderImpl().apply(block).build()
        }

        override fun toString() = buildString {
            append("GetFooOutput(")
            append("sparseStructMap=$sparseStructMap)")
        }

        override fun hashCode(): Int {
            var result = sparseStructMap?.hashCode() ?: 0
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true

            other as GetFooOutput

            if (sparseStructMap != other.sparseStructMap) return false

            return true
        }

        fun copy(block: DslBuilder.() -> Unit = {}): GetFooOutput = BuilderImpl(this).apply(block).build()

        interface Builder {
            fun build(): GetFooOutput
            fun sparseStructMap(sparseStructMap: Map<String, Greeting?>): Builder
        }

        interface DslBuilder {
            var sparseStructMap: Map<String, Greeting?>?

            fun build(): GetFooOutput
        }

        private class BuilderImpl() : Builder, DslBuilder {
            override var sparseStructMap: Map<String, Greeting?>? = null

            constructor(x: GetFooOutput) : this() {
                this.sparseStructMap = x.sparseStructMap
            }

            override fun build(): GetFooOutput = GetFooOutput(this)
            override fun sparseStructMap(sparseStructMap: Map<String, Greeting?>): Builder = apply { this.sparseStructMap = sparseStructMap }
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
            private val SPARSESTRUCTMAP_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, "sparseStructMap".toSerialNames())
            private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
                trait(XmlSerialName("GetFoo"))
                field(SPARSESTRUCTMAP_DESCRIPTOR)
            }
        }

        suspend fun deserialize(deserializer: Deserializer): GetFooOutput {
            val builder = GetFooOutput.dslBuilder()

            deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                loop@while (true) {
                    when (findNextFieldIndex()) {
                        SPARSESTRUCTMAP_DESCRIPTOR.index ->
                            builder.sparseStructMap =
                                deserializer.deserializeMap(SPARSESTRUCTMAP_DESCRIPTOR) {
                                    val map0 = mutableMapOf<String, Greeting?>()
                                    while (hasNextEntry()) {
                                        val k0 = key()
                                        val el0 = when (nextHasValue()) {
                                            true -> GreetingDeserializer().deserialize(deserializer)
                                            false -> deserializeNull()
                                        }
                                        map0[k0] = el0
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

    class GreetingDeserializer {

        companion object {
            private val SAYING_DESCRIPTOR = SdkFieldDescriptor(SerialKind.String, "saying".toSerialNames())
            private val OBJ_DESCRIPTOR = SdkObjectDescriptor.build() {
                trait(XmlSerialName("Greeting"))
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
            assertNull(struct.sparseStructMap)
        }
    }

    @Test
    fun itDeserializesAnEmptyMapIntoAnStructWithEmptyMap() = runSuspendTest {
        val jsonPayload = """
            {
                "sparseStructMap": {}
            }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <GetFoo>
                <sparseStructMap />
            </GetFoo>
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(jsonSerdeProvider.deserializer(jsonPayload), xmlSerdeProvider.deserializer(xmlPayload))) {
            val struct = GetFooDeserializer().deserialize(deserializer)

            assertNotNull(struct)
            assertNotNull(struct.sparseStructMap)
            assertTrue(struct.sparseStructMap.isEmpty())
        }
    }

    @Test
    fun itDeserializesAMapWithNullValuesIntoAnStructWithMapContainingKeysWithNullValues() = runSuspendTest {
        val jsonPayload = """
            {
            	"sparseStructMap": {
            		"greeting1": {
            			"saying": "boo"
            		},
            		"greeting2": null,
            		"greeting3": {
            			"saying": "hoo"
            		}
            	}
            }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <GetFoo>
                <sparseStructMap>
                    <entry>
                        <key>greeting1</key>
                        <value>
                            <Greeting>
                                <saying>boo</saying>
                            </Greeting>
                        </value>
                    </entry>
                    <entry>
                        <key>greeting2</key>
                        <value />                            
                    </entry>
                    <entry>
                        <key>greeting3</key>
                        <value>
                            <Greeting>
                                <saying>hoo</saying>
                            </Greeting>
                        </value>
                    </entry>
                </sparseStructMap>
            </GetFoo>
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(jsonSerdeProvider.deserializer(jsonPayload), xmlSerdeProvider.deserializer(xmlPayload))) {
            val struct = GetFooDeserializer().deserialize(deserializer)

            assertNotNull(struct)
            assertNotNull(struct.sparseStructMap)
            assertEquals(3, struct.sparseStructMap.size)
            struct.sparseStructMap.shouldContainKeys("greeting1", "greeting2", "greeting3")
            assertNull(struct.sparseStructMap["greeting2"])
        }
    }
}
