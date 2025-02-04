/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package aws.smithy.kotlin.runtime.serde.cbor

import aws.smithy.kotlin.runtime.IgnoreNative
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
        val payload = "0xfb00000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeDouble()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0x1900".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeShort()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "0x1a000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0x3900".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeShort()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0x5900".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "0x5b00000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0xd900".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "0xda000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - unexpected minor value 31`() {
        val payload = "0x1f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - list - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "0x98".toByteArray()

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
        val payload = "0x9a000000".toByteArray()

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
        val payload = "0x9b00000000000000".toByteArray()

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
        val payload = "0xb8".toByteArray()

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
        val payload = "0xba000000".toByteArray()

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
        val payload = "0xfa000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeFloat()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0x7900".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - list - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0x9900".toByteArray()

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
        val payload = "0x3f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "0x7b00000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - major7 - unexpected minor value 31`() {
        val payload = "0xff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "0x78".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - unexpected minor value 31`() {
        val payload = "0xdf".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "0x58".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - slice - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "0x5a000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - map - 2 - arg len 2 greater than remaining buf len`() {
        val payload = "0xb900".toByteArray()

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
        val payload = "0xbb00000000000000".toByteArray()

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
        val payload = "0xdb00000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "0x18".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - major7 - float16 - incomplete float16 at end of buf`() {
        val payload = "0xf900".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeFloat()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - uint - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "0x1b00000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeLong()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "0x38".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByte()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "0x3a000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeInt()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - negint - 8 - arg len 8 greater than remaining buf len`() {
        val payload = "0x3b00000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeLong()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - string - 4 - arg len 4 greater than remaining buf len`() {
        val payload = "0x7a000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidArgument - tag - 1 - arg len 1 greater than remaining buf len`() {
        val payload = "0xd8".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidList - indefinite list  -  invalid item - arg len 1 greater than remaining buf len`() {
        val payload = "0x9f18".toByteArray()

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
        val payload = "0x81".toByteArray()

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
        val payload = "0x8118".toByteArray()

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
        val payload = "0x9f".toByteArray()

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
        val payload = "0xa100".toByteArray()

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
        val payload = "0xa17801".toByteArray()

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
        val payload = "0xa163666f6f18".toByteArray()

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
        val payload = "0xbf".toByteArray()

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
        val payload = "0xbf00".toByteArray()

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
        val payload = "0xbf7801".toByteArray()

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
        val payload = "0xbf63666f6f18".toByteArray()

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
        val payload = "0xa1".toByteArray()

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
        val payload = "0x5f5801".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - no break - expected break marker`() {
        val payload = "0x7f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - invalid nested major - unexpected major type 2 in indefinite slice`() {
        val payload = "0x7f40".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - nested indefinite - nested indefinite slice`() {
        val payload = "0x7f7f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - invalid nested definite - decode subslice - slice len 1 greater than remaining buf len`() {
        val payload = "0x7f7801".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - invalid nested major - unexpected major type 3 in indefinite slice`() {
        val payload = "0x5f60".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - no break - expected break marker`() {
        val payload = "0x5f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - nested indefinite - nested indefinite slice`() {
        val payload = "0x5f5f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - string - 1 - not enough bytes - slice len 1 greater than remaining buf len`() {
        val payload = "0x7801".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeString()
        }
    }

    @Test
    fun `TestDecode_InvalidSlice - slice - 1 - not enough bytes - slice len 1 greater than remaining buf len`() {
        val payload = "0x5801".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)

        assertFails {
            deserializer.deserializeByteArray()
        }
    }

    @Test
    fun `TestDecode_InvalidTag - invalid value - arg len 1 greater than remaining buf len`() {
        val payload = "0xc118".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }

    @Test
    fun `TestDecode_InvalidTag - eof - unexpected end of payload`() {
        val payload = "0xc1".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }

        assertFails {
            Tag.decode(buffer)
        }
    }
}
