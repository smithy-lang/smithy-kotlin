$version: "1.0"

namespace com.test

use smithy.protocols#rpcv2Cbor

@rpcv2Cbor
@httpBearerAuth
service ServiceGeneratorTest {
    version: "1.0.0"
    operations: [
        GetTest,
        PostTest,
        AuthTest,
    ]
}

@readonly
@auth([])
@http(method: "GET", uri: "/get", code: 200)
operation GetTest {
    input: GetTestInput
    output: GetTestOutput
}

@input
structure GetTestInput {}

@output
structure GetTestOutput {}


@http(method: "POST", uri: "/post", code: 201)
@auth([])
operation PostTest {
    input: PostTestInput
    output: PostTestOutput
}

@input
structure PostTestInput {
    input1: String
    input2: Integer
}

@output
structure PostTestOutput {
    output1: String
    output2: Integer
}

@http(method: "POST", uri: "/auth", code: 201)
operation AuthTest {
    input: AuthTestInput
    output: AuthTestOutput
}

@input
structure AuthTestInput {
    input1: String
}

@output
structure AuthTestOutput {
    output1: String
}


