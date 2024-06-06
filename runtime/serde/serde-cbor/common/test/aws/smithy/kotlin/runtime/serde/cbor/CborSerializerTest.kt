import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.cbor.CborPrimitiveDeserializer
import aws.smithy.kotlin.runtime.serde.cbor.CborSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class CborSerializerTest {
    @Test
    fun testBoolean() {
        val tests = listOf(true, false, true, false, false)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeBoolean(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it, deserializer.deserializeBoolean())
        }
    }

    @Test
    fun testByte() {
        val tests = listOf(Byte.MIN_VALUE, -34, 0, 39, Byte.MAX_VALUE)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeByte(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        tests.forEach {
            assertEquals(it, deserializer.deserializeByte())
        }
    }

    @Test
    fun testChar() {
        val tests = listOf(
            'a', 'z', 'h', 'e', 'l', 'l', 'o',
            'A', 'Z', 'H', 'E', 'L', 'L', 'O',
            '1', '2', '3', '4', '5', '6', '7',
            '!', '@', '#', '$', '%', '^', '&', '*', '(', ')',
            '\n', '\t', '\r', ' '
        )
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeChar(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        tests.forEach {
            assertEquals(it.toString(), deserializer.deserializeString())
        }
    }

    @Test
    fun testInt() {
        val tests = listOf(Int.MIN_VALUE, -34, 0, 39, 402, Int.MAX_VALUE)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeInt(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        tests.forEach {
            assertEquals(it, deserializer.deserializeInt())
        }
    }

    @Test
    fun testLong() {
        val tests = listOf(Long.MIN_VALUE, -34, 0, 39, 402, Long.MAX_VALUE)
        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeLong(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)
        tests.forEach {
            assertEquals(it, deserializer.deserializeLong())
        }
    }

    @Test
    fun testFloat() {
        val tests = listOf(
            10f, // Floating-point numeric types will be serialized into non-floating-point numeric types if and only if the conversion would not cause a loss of precision.
            (Int.MAX_VALUE.toLong() + 1L).toFloat(),
            Float.NaN, Float.NEGATIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE, Float.POSITIVE_INFINITY,
            123.456f,
            0.00432f,
            0.235f,
            3.141592f,
            6.2831855f
        )

        val serializer = CborSerializer()

        tests.forEach {
            serializer.serializeFloat(it)
        }

        val buffer = SdkBuffer().apply { write(serializer.toByteArray()) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertEquals(10, deserializer.deserializeInt())
        assertEquals((Int.MAX_VALUE.toLong() + 1L), deserializer.deserializeLong())
        tests.drop(0).forEach {
            assertEquals(it, deserializer.deserializeFloat())
        }
    }

}