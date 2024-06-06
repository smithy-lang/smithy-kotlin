import aws.smithy.kotlin.runtime.awsprotocol.rpcv2.cbor.Rpcv2CborErrorDeserializer
import aws.smithy.kotlin.runtime.http.Headers
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SdkObjectDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.cbor.CborSerialName
import aws.smithy.kotlin.runtime.serde.cbor.CborSerializer
import aws.smithy.kotlin.runtime.serde.serializeStruct
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class Rpcv2CborErrorDeserializerTest {
    @Test
    fun testDeserializeErrorType() = runTest {
        val tests = listOf(
            "FooError",
            "FooError:http://amazon.com/smithy/com.amazon.smithy.validate/",
            "aws.protocoltests.rpcv2.cbor#FooError",
            "aws.protocoltests.rpcv2.cbor#FooError:http://amazon.com/smithy/com.amazon.smithy.validate/",
        )

        val expected = "FooError"

        val errorTypeFieldDescriptor = SdkFieldDescriptor(SerialKind.String, CborSerialName("__type"))
        val errorResponseObjectDescriptor = SdkObjectDescriptor.build {
            field(errorTypeFieldDescriptor)
        }

        tests.forEach { errorType ->
            val serializer = CborSerializer()

            serializer.serializeStruct(errorResponseObjectDescriptor) {
                field(errorTypeFieldDescriptor, errorType)
            }

            val bytes = serializer.toByteArray()

            val actual = Rpcv2CborErrorDeserializer.deserialize(bytes)
            assertEquals(expected, actual.code)
        }
    }
}