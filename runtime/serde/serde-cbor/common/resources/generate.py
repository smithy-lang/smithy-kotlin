import json

fp = "./cbor-decode-success-tests.json"



print("class CborDeserializeSuccessTest {")

tests = json.loads(open(fp, "r").read())

for test in tests:
    # print(f"DEBUG: {test}")

    description = test["description"].replace("{", "").replace("}", "").replace("/", " - ")

    print(f"""
    @Test
    fun `{description}`()""", end = "")
    print(" { ")
    print(f"""
        val payload = "0x{test['input']}".toByteArray()""")
    print("""
        val buffer = SdkBuffer().apply { write(payload) }
        val deserializer = CborPrimitiveDeserializer(buffer)
          
        val result = deserializer.deserialize
        assertEquals(, result)
    }""")
    


# def get_deserializer_for_type(type):
    # if type == "null": return "deserializeNull()"
    # elif type == "float64": return "TODO(What deserialize function this be?)"
    # elif type == "uint": return "deserialize"
    