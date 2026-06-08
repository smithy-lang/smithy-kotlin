/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.io.SdkBuffer
import aws.smithy.kotlin.runtime.serde.SdkFieldDescriptor
import aws.smithy.kotlin.runtime.serde.SerialKind
import aws.smithy.kotlin.runtime.serde.cbor.encoding.Tag
import aws.smithy.kotlin.runtime.serde.deserializeList
import aws.smithy.kotlin.runtime.serde.deserializeMap
import kotlin.test.Test
import kotlin.test.assertFails

class CborDeserializerErrorTest {
    @Test
    fun `TestDecode_InvalidArgument - major7 - float64 - incomplete float64 at end of buf`() {
        val payload = "fb00000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeDouble()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "1900".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeShort()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "1a000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "3900".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeShort()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "5900".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "5b00000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "d900".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "da000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - unexpected minor value 31`() {
        val payload = "1f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - list - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "98".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - list - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "9a000000".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - list - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "9b00000000000000".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - map - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "b8".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - map - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "ba000000".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - major7 - float32 - incomplete float32 at end of buf`() {
        val payload = "fa000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeFloat()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "7900".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - list - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "9900".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - unexpected minor value 31`() {
        val payload = "3f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "7b00000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - major7 - unexpected minor value 31`() {
        val payload = "ff".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "78".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - unexpected minor value 31`() {
        val payload = "df".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "58".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "5a000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - map - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "b900".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - map - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "bb00000000000000".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "db00000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "18".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - major7 - float16 - incomplete float16 at end of buf`() {
        val payload = "f900".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeFloat()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "1b00000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeLong()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "38".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByte()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "3a000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "3b00000000000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeLong()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "7a000000".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "d8".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidList - indefinite list  -  invalid item - arg len 1 greater than remaining buf len`() {
        val payload = "9f18".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidList - list  -  eof after head - unexpected end of payload`() {
        val payload = "81".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidList - list  -  invalid item - arg len 1 greater than remaining buf len`() {
        val payload = "8118".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidList - indefinite list - no break - expected break marker`() {
        val payload = "9f".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeList(SdkFieldDescriptor(SerialKind.List)) {
                while (hasNextElement()) {
                    deserializeInt()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - map  -  non-string key - unexpected major type 0 for map key`() {
        val payload = "a100".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - map  -  invalid key - slice len 1 greater than remaining buf len`() {
        val payload = "a17801".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - map  -  invalid value - arg len 1 greater than remaining buf len`() {
        val payload = "a163666f6f18".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - indefinite map  -  no break - expected break marker`() {
        val payload = "bf".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - indefinite map  -  non-string key - unexpected major type 0 for map key`() {
        val payload = "bf00".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - indefinite map  -  invalid key - slice len 1 greater than remaining buf len`() {
        val payload = "bf7801".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - indefinite map  -  invalid value - arg len 1 greater than remaining buf len`() {
        val payload = "bf63666f6f18".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidMap - map  -  eof after head - unexpected end of payload`() {
        val payload = "a1".hexToByteArray()

        val deserializer = CborDeserializer(payload)
        assertFails {
            deserializer.deserializeMap(SdkFieldDescriptor(SerialKind.Map)) {
                while (hasNextEntry()) {
                    deserializeString()
                    deserializeString()
                }
            }
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - invalid nested definite - decode subslice slice len 1 greater than remaining buf len`() {
        val payload = "5f5801".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - no break - expected break marker`() {
        val payload = "7f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - invalid nested major - unexpected major type 2 in indefinite slice`() {
        val payload = "7f40".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - nested indefinite - nested indefinite slice`() {
        val payload = "7f7f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - invalid nested definite - decode subslice - slice len 1 greater than remaining buf len`() {
        val payload = "7f7801".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - invalid nested major - unexpected major type 3 in indefinite slice`() {
        val payload = "5f60".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - no break - expected break marker`() {
        val payload = "5f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - nested indefinite - nested indefinite slice`() {
        val payload = "5f5f".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - 1 - not enough bytes - slice len 1 greater than remaining buf len`() {
        val payload = "7801".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - 1 - not enough bytes - slice len 1 greater than remaining buf len`() {
        val payload = "5801".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidTag - invalid value - arg len 1 greater than remaining buf len`() {
        val payload = "c118".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidTag - eof - unexpected end of payload`() {
        val payload = "c1".hexToByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }
}
