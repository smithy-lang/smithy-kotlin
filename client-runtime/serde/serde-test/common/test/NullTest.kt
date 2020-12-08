import software.aws.clientrt.serde.*
import software.aws.clientrt.serde.json.JsonDeserializer
import software.aws.clientrt.serde.xml.XmlDeserializer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NullTest {

    class AnonStruct {
        var x: Int? = null
        var y: Int? = null
        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer)
            val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "AnonStruct"
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): AnonStruct? {
                val result = AnonStruct()
                return if (deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                    loop@ while (true) {
                        when (findNextFieldIndex()) {
                            X_DESCRIPTOR.index -> result.x = deserializeInt()
                            Y_DESCRIPTOR.index -> result.y = deserializeInt()
                            null -> break@loop
                            else -> throw RuntimeException("unexpected field in BasicStructTest deserializer")
                        }
                    }
                }) result else null
            }
        }
    }

    class ParentStruct {
        var childStruct: ChildStruct? = null

        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor("ChildStruct", SerialKind.Struct)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                field(X_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): ParentStruct? {
                val result = ParentStruct()
                return if (deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                        loop@ while (true) {
                            when (findNextFieldIndex()) {
                                X_DESCRIPTOR.index -> result.childStruct = ChildStruct.deserialize(deserializer)
                                null -> break@loop
                                else -> throw RuntimeException("unexpected field in BasicStructTest deserializer")
                            }
                        }
                    }) result else null
            }
        }
    }

    class ChildStruct {
        var x: Int? = null
        var y: Int? = null
        companion object {
            val X_DESCRIPTOR = SdkFieldDescriptor("x", SerialKind.Integer)
            val Y_DESCRIPTOR = SdkFieldDescriptor("y", SerialKind.Integer)
            val OBJ_DESCRIPTOR = SdkObjectDescriptor.build {
                serialName = "ChildStruct"
                field(X_DESCRIPTOR)
                field(Y_DESCRIPTOR)
            }

            fun deserialize(deserializer: Deserializer): ChildStruct? {
                val result = ChildStruct()
                return if (deserializer.deserializeStruct(OBJ_DESCRIPTOR) {
                        loop@ while (true) {
                            when (findNextFieldIndex()) {
                                X_DESCRIPTOR.index -> result.x = deserializeInt()
                                Y_DESCRIPTOR.index -> result.y = deserializeInt()
                                null -> break@loop
                                else -> throw RuntimeException("unexpected field in ChildStruct deserializer")
                            }
                        }
                    }) result else null
            }
        }
    }

    /**
     * Empty objects should deserialize into empty instances of their target type.
     */
    @Test
    fun `it deserializes an empty document into an empty anonymous struct`() {
        val jsonPayload = "{}".encodeToByteArray()
        val xmlPayload = "<AnonStruct />".encodeToByteArray()

        for (deserializer in listOf(JsonDeserializer(jsonPayload), XmlDeserializer(xmlPayload))) {
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
    fun `it deserializes a reference to a null object`() {
        val jsonPayload = """
            { "ChildStruct" : null }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <ParentStruct />                
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(JsonDeserializer(jsonPayload), XmlDeserializer(xmlPayload))) {
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
    fun `it deserializes a reference to an empty object`() {
        val jsonPayload = """
            { "ChildStruct" : {}} }
        """.trimIndent().encodeToByteArray()
        val xmlPayload = """
            <ParentStruct>
                <ChildStruct />
            </ParentStruct>
        """.trimIndent().encodeToByteArray()

        for (deserializer in listOf(JsonDeserializer(jsonPayload), XmlDeserializer(xmlPayload))) {
            val struct = ParentStruct.deserialize(deserializer)

            assertNotNull(struct)
            assertNotNull(struct.childStruct)
            assertNull(struct.childStruct!!.x)
            assertNull(struct.childStruct!!.y)
        }
    }
}