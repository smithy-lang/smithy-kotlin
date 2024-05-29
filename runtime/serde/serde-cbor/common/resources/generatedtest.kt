class CborDeserializeSuccessTest {

    @Test
    fun `atomic - undefined`() { 

        val payload = "0xf7".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float64 - 1.625`() { 

        val payload = "0xfb3ffa000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 0 - max`() { 

        val payload = "0x17".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 8 - min`() { 

        val payload = "0x1b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 8 - max`() { 

        val payload = "0x1bffffffffffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 8 - min`() { 

        val payload = "0x3b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - true`() { 

        val payload = "0xf5".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 4 - min`() { 

        val payload = "0x1a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 4 - max`() { 

        val payload = "0x1affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 1 - min`() { 

        val payload = "0x3800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float16 - subnormal`() { 

        val payload = "0xf90050".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float16 - NaN - LSB`() { 

        val payload = "0xf97c01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint,1,min`() { 

        val payload = "0x1800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 0 - min`() { 

        val payload = "0x20".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float16 - -Inf`() { 

        val payload = "0xf9fc00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 8 - max`() { 

        val payload = "0x3bfffffffffffffffe".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 0 - min`() { 

        val payload = "0x00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 1 - max`() { 

        val payload = "0x18ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 2 - min`() { 

        val payload = "0x190000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 1 - max`() { 

        val payload = "0x38ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 2 - min`() { 

        val payload = "0x390000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float64 - +Inf`() { 

        val payload = "0xfb7ff0000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 4 - min`() { 

        val payload = "0x3a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 4 - max`() { 

        val payload = "0x3affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float16 - NaN - MSB`() { 

        val payload = "0xf97e00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float32 - +Inf`() { 

        val payload = "0xfa7f800000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - uint - 2 - max`() { 

        val payload = "0x19ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 2 - max`() { 

        val payload = "0x39ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - false`() { 

        val payload = "0xf4".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - null`() { 

        val payload = "0xf6".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - negint - 0 - max`() { 

        val payload = "0x37".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float16 - +Inf`() { 

        val payload = "0xf97c00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `atomic - float32 - 1.625`() { 

        val payload = "0xfa3fd00000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `definite slice - len = 0`() { 

        val payload = "0x40".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `definite slice - len > 0`() { 

        val payload = "0x43666f6f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `definite string - len = 0`() { 

        val payload = "0x60".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `definite string - len > 0`() { 

        val payload = "0x63666f6f".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite slice - len > 0, len = 0`() { 

        val payload = "0x5f43666f6f40ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite slice - len > 0, len > 0`() { 

        val payload = "0x5f43666f6f43666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite slice - len = 0`() { 

        val payload = "0x5fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite slice - len = 0, explicit`() { 

        val payload = "0x5f40ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite slice - len = 0, len > 0`() { 

        val payload = "0x5f4043666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite string - len = 0`() { 

        val payload = "0x7fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite string - len = 0, explicit`() { 

        val payload = "0x7f60ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite string - len = 0, len > 0`() { 

        val payload = "0x7f6063666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite string - len > 0, len = 0`() { 

        val payload = "0x7f63666f6f60ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `indefinite string - len > 0, len > 0`() { 

        val payload = "0x7f63666f6f63666f6fff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 1 - max]`() { 

        val payload = "0x8118ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 8 - min]`() { 

        val payload = "0x811b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 1 - min]`() { 

        val payload = "0x9f1800ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 2 - max]`() { 

        val payload = "0x9f19ffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 2 - min]`() { 

        val payload = "0x9f390000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 4 - max]`() { 

        val payload = "0x811affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 8 - min]`() { 

        val payload = "0x9f1b0000000000000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 2 - max]`() { 

        val payload = "0x9f39ffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ float16 - NaN - LSB]`() { 

        val payload = "0x9ff97c01ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 1 - max]`() { 

        val payload = "0x8138ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 2 - min]`() { 

        val payload = "0x81390000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [null]`() { 

        val payload = "0x81f6".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [float16 - -Inf]`() { 

        val payload = "0x81f9fc00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 4 - min]`() { 

        val payload = "0x9f1a00000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 1 - min]`() { 

        val payload = "0x811800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 0 - max]`() { 

        val payload = "0x9f17ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 0 - min]`() { 

        val payload = "0x9f20ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 1 - max]`() { 

        val payload = "0x9f38ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ null]`() { 

        val payload = "0x9ff6ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 1 - max]`() { 

        val payload = "0x9f18ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 4 - max]`() { 

        val payload = "0x9f1affffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 8 - max]`() { 

        val payload = "0x9f1bffffffffffffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ true]`() { 

        val payload = "0x9ff5ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ undefined]`() { 

        val payload = "0x9ff7ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 0 - max]`() { 

        val payload = "0x8117".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 8 - max]`() { 

        val payload = "0x811bffffffffffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 0 - min]`() { 

        val payload = "0x8120".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 0 - max]`() { 

        val payload = "0x8137".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 4 - min]`() { 

        val payload = "0x813a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [true]`() { 

        val payload = "0x81f5".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [float32]`() { 

        val payload = "0x81fa7f800000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [float64]`() { 

        val payload = "0x81fb7ff0000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 2 - min]`() { 

        val payload = "0x9f190000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ float16 - NaN - MSB]`() { 

        val payload = "0x9ff97e00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 0 - max]`() { 

        val payload = "0x9f37ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 1 - min]`() { 

        val payload = "0x9f3800ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 8 - min]`() { 

        val payload = "0x9f3b0000000000000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 8 - max]`() { 

        val payload = "0x9f3bfffffffffffffffeff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [false]`() { 

        val payload = "0x81f4".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ uint - 0 - min]`() { 

        val payload = "0x9f00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 4 - min]`() { 

        val payload = "0x9f3a00000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ negint - 4 - max]`() { 

        val payload = "0x9f3affffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ float16 - +Inf]`() { 

        val payload = "0x9ff97c00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 0 - min]`() { 

        val payload = "0x8100".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 1 - min]`() { 

        val payload = "0x813800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ float16 - -Inf]`() { 

        val payload = "0x9ff9fc00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ float32]`() { 

        val payload = "0x9ffa7f800000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 2 - min]`() { 

        val payload = "0x81190000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 4 - min]`() { 

        val payload = "0x811a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [float16 - +Inf]`() { 

        val payload = "0x81f97c00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ float64]`() { 

        val payload = "0x9ffb7ff0000000000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [float16 - NaN - MSB]`() { 

        val payload = "0x81f97e00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [float16 - NaN - LSB]`() { 

        val payload = "0x81f97c01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [_ false]`() { 

        val payload = "0x9ff4ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 8 - min]`() { 

        val payload = "0x813b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 8 - max]`() { 

        val payload = "0x813bfffffffffffffffe".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [undefined]`() { 

        val payload = "0x81f7".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [uint - 2 - max]`() { 

        val payload = "0x8119ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 2 - max]`() { 

        val payload = "0x8139ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `list - [negint - 4 - max]`() { 

        val payload = "0x813affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 8 - max`() { 

        val payload = "0xbf63666f6f1bffffffffffffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - null`() { 

        val payload = "0xa163666f6ff6".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 4 - max`() { 

        val payload = "0xbf63666f6f3affffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ float16 - -Inf`() { 

        val payload = "0xbf63666f6ff9fc00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 2 - max`() { 

        val payload = "0xa163666f6f19ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 1 - min`() { 

        val payload = "0xa163666f6f3800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ undefined`() { 

        val payload = "0xbf63666f6ff7ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 0 - max`() { 

        val payload = "0xa163666f6f17".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 0 - max`() { 

        val payload = "0xbf63666f6f17ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 1 - min`() { 

        val payload = "0xbf63666f6f1800ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 8 - min`() { 

        val payload = "0xbf63666f6f1b0000000000000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 8 - max`() { 

        val payload = "0xbf63666f6f3bfffffffffffffffeff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 2 - min`() { 

        val payload = "0xa163666f6f190000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ float16 - NaN - MSB`() { 

        val payload = "0xbf63666f6ff97e00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 0 - min`() { 

        val payload = "0xa163666f6f20".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - float16 - -Inf`() { 

        val payload = "0xa163666f6ff9fc00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 1 - max`() { 

        val payload = "0xbf63666f6f38ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 8 - min`() { 

        val payload = "0xbf63666f6f3b0000000000000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 1 - min`() { 

        val payload = "0xa163666f6f1800".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 2 - min`() { 

        val payload = "0xbf63666f6f190000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 2 - max`() { 

        val payload = "0xbf63666f6f19ffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 0 - max`() { 

        val payload = "0xbf63666f6f37ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 2 - max`() { 

        val payload = "0xbf63666f6f39ffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - true`() { 

        val payload = "0xa163666f6ff5".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ true`() { 

        val payload = "0xbf63666f6ff5ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ false`() { 

        val payload = "0xbf63666f6ff4ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 8 - max`() { 

        val payload = "0xa163666f6f1bffffffffffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - float16 - NaN - LSB`() { 

        val payload = "0xa163666f6ff97c01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 0 - min`() { 

        val payload = "0xbf63666f6f00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 4 - min`() { 

        val payload = "0xbf63666f6f3a00000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ float32`() { 

        val payload = "0xbf63666f6ffa7f800000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 0 - min`() { 

        val payload = "0xa163666f6f00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 1 - max`() { 

        val payload = "0xa163666f6f38ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - float64`() { 

        val payload = "0xa163666f6ffb7ff0000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ float16 - NaN - LSB`() { 

        val payload = "0xbf63666f6ff97c01ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 8 - min`() { 

        val payload = "0xa163666f6f1b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 8 - max`() { 

        val payload = "0xa163666f6f3bfffffffffffffffe".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - undefined`() { 

        val payload = "0xa163666f6ff7".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - float16 - NaN - MSB`() { 

        val payload = "0xa163666f6ff97e00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 8 - min`() { 

        val payload = "0xa163666f6f3b0000000000000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 4 - max`() { 

        val payload = "0xbf63666f6f1affffffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 1 - min`() { 

        val payload = "0xbf63666f6f3800ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ float16 - +Inf`() { 

        val payload = "0xbf63666f6ff97c00ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 2 - min`() { 

        val payload = "0xa163666f6f390000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - false`() { 

        val payload = "0xa163666f6ff4".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - float32`() { 

        val payload = "0xa163666f6ffa7f800000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 1 - max`() { 

        val payload = "0xbf63666f6f18ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 0 - max`() { 

        val payload = "0xa163666f6f37".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 4 - max`() { 

        val payload = "0xa163666f6f3affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - float16 - +Inf`() { 

        val payload = "0xa163666f6ff97c00".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ float64`() { 

        val payload = "0xbf63666f6ffb7ff0000000000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 1 - max`() { 

        val payload = "0xa163666f6f18ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 4 - max`() { 

        val payload = "0xa163666f6f1affffffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 2 - max`() { 

        val payload = "0xa163666f6f39ffff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ uint - 4 - min`() { 

        val payload = "0xbf63666f6f1a00000000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 0 - min`() { 

        val payload = "0xbf63666f6f20ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ null`() { 

        val payload = "0xbf63666f6ff6ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - uint - 4 - min`() { 

        val payload = "0xa163666f6f1a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - negint - 4 - min`() { 

        val payload = "0xa163666f6f3a00000000".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `map - _ negint - 2 - min`() { 

        val payload = "0xbf63666f6f390000ff".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 8 - min`() { 

        val payload = "0xdb000000000000000001".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 0 - min`() { 

        val payload = "0xc001".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 4 - min`() { 

        val payload = "0xda0000000001".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 4 - max`() { 

        val payload = "0xdaffffffff01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 2 - min`() { 

        val payload = "0xd9000001".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 2 - max`() { 

        val payload = "0xd9ffff01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 8 - max`() { 

        val payload = "0xdbffffffffffffffff01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 0 - max`() { 

        val payload = "0xd701".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 1 - min`() { 

        val payload = "0xd80001".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }

    @Test
    fun `tag - 1 - max`() { 

        val payload = "0xd8ff01".toByteArray()

        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }
