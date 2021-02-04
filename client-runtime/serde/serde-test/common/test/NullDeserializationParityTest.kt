import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.json.JsonDeserializer
import software.aws.clientrt.serde.json.JsonSerialName
import software.aws.clientrt.serde.xml.XmlDeserializer
import software.aws.clientrt.serde.xml.XmlDeserializer2
import software.aws.clientrt.serde.xml.XmlSerialName
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

fun String.toSerialNames(): Set<FieldTrait> = setOf(JsonSerialName(this), XmlSerialName(this))

class NullDeserializationParityTest {
    class AnonStruct {
        var x: Int? = null
        var y: Int? = null
        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, "x".toSerialNames())
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, "y".toSerialNames())
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("AnonStruct"))
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): AnonStruct {
                val result = AnonStruct()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_DESCRIPTOR.index -> result.x = deserializeInt()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
                            null -> break@loop
                            else -> throw RuntimeException("unexpected field in BasicStructTest deserializer")
                        }
                    }
                }
                return result
            }
        }
    }

    class ParentStruct {
        var childStruct: ChildStruct? = null

        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Map, JsonSerialName("ChildStruct"), XmlSerialName("ChildStruct"))
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("ParentStruct"))
                field(X_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): ParentStruct {
                val result = ParentStruct()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_DESCRIPTOR.index -> result.childStruct = ChildStruct.deserialize(deserializer)
                            null -> break@loop
                            else -> throw RuntimeException("unexpected field in BasicStructTest deserializer")
                        }
                    }
                }
                return result
            }
        }
    }

    class ChildStruct {
        var x: Int? = null
        var y: Int? = null
        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, "x".toSerialNames())
            val Y_DESCRIPTOR = SdkFieldDescriptor(SerialKind.Integer, "y".toSerialNames())
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                trait(XmlSerialName("ChildStruct"))
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): ChildStruct {
                val result = ChildStruct()
                deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_DESCRIPTOR.index -> result.x = deserializeInt()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
                            null -> break@loop
                            else -> throw RuntimeException("unexpected field in ChildStruct deserializer")
                        }
                    }
                }
                return result
            }
        }
    }

    /**
     * Empty objects should deserialize into empty instances of their target type.
     */
    @Test
    fun itDeserializesAnEmptyDocumentIntoAnEmptyAnonymousStruct() {
        val jsonPayload = "{}".encodeToByteArray()
        val xmlPayload = "<AnonStruct />".encodeToByteArray()

        for (deserializer in listOf(JsonDeserializer(jsonPayload), XmlDeserializer2(xmlPayload))) {
            val struct = AnonStruct.deserialize(deserializer)

            assertNotNull(struct)
            assertNull(struct.x)
            assertNull(struct.y)
        }
    }

    /**
     * Inputs that specify the value of an object as null, or do not reference the child at all should
     * deserialize those children as null references.
     */
    @Test
    fun itDeserializesAReferenceToANullObject() {
        val jsonPayload = """
            { "ChildStruct" : null }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <ParentStruct />                
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(JsonDeserializer(jsonPayload), XmlDeserializer2(xmlPayload))) {
            val struct = ParentStruct.deserialize(deserializer)

            assertNotNull(struct)
            assertNull(struct.childStruct)
        }
    }

    /**
     * Inputs that refer to children as empty elements should deserialize such that
     * those deserialized children exist but are empty.
     */
    @Test
    fun itDeserializesAReferenceToAnEmptyObject() {
        val jsonPayload = """
            { "ChildStruct" : {}} }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <ParentStruct>
                <ChildStruct />
            </ParentStruct>
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(JsonDeserializer(jsonPayload), XmlDeserializer2(xmlPayload))) {
            val struct = ParentStruct.deserialize(deserializer)

            assertNotNull(struct)
            assertNotNull(struct.childStruct)
            assertNull(struct.childStruct!!.x)
            assertNull(struct.childStruct!!.y)
        }
    }
}
