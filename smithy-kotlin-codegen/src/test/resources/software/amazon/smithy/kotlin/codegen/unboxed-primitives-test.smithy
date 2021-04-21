$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
service Test {
    version: "1.0.0",
    operations: [
        UnboxedPrimitivesTest
    ]
}

@http(method: "POST", uri: "/UnboxedPrimitivesTest")
operation UnboxedPrimitivesTest {
    output: UnboxedPrimitivesTestResponse
}

structure UnboxedPrimitivesTestResponse {
    payload1: PrimitiveInteger,
    payload2: PrimitiveBoolean,
    payload3: PrimitiveByte,
    payload4: PrimitiveShort,
    payload5: PrimitiveLong,
    payload6: PrimitiveFloat,
    payload7: PrimitiveDouble
}