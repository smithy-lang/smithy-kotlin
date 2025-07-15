$version: "1.0"

namespace com.test

use smithy.protocols#rpcv2Cbor
//use smithy.api#http
//use smithy.api#httpQuery
//use smithy.api#readonly
//use smithy.api#idempotent

@rpcv2Cbor
service ServiceGeneratorTest {
    version: "1.0.0"
    operations: [
        GetTest,
        PostTest,
        PutTest,
        PatchTest,
        DeleteTest
    ]
}

/// ------------------------------------------------------------------
/// GET
/// ------------------------------------------------------------------
@readonly
@http(method: "GET", uri: "/get", code: 200)
operation GetTest {
    input: GetTestInput
    output: GetTestOutput
}

@input
structure GetTestInput {}

@output
structure GetTestOutput {
    output1: String
}

/// ------------------------------------------------------------------
/// POST
/// ------------------------------------------------------------------
@http(method: "POST", uri: "/post", code: 201)
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

/// ------------------------------------------------------------------
/// PUT
/// ------------------------------------------------------------------
@idempotent
@http(method: "PUT", uri: "/put", code: 200)
operation PutTest {
    input: PutTestInput
    output: PutTestOutput
}

@input
structure PutTestInput {
    input1: String
    input2: Integer
}

@output
structure PutTestOutput {
    output1: String
    output2: Integer
}

/// ------------------------------------------------------------------
/// PATCH
/// ------------------------------------------------------------------
@http(method: "PATCH", uri: "/patch", code: 200)
operation PatchTest {
    input: PatchTestInput
    output: PatchTestOutput
}

@input
structure PatchTestInput {
    input1: String
    input2: Integer
}

@output
structure PatchTestOutput {
    output1: String
    output2: Integer
}

/// ------------------------------------------------------------------
/// DELETE
/// ------------------------------------------------------------------
@idempotent
@http(method: "DELETE", uri: "/delete", code: 204)
operation DeleteTest {
    input: DeleteTestInput
    output: DeleteTestOutput
}

@input
structure DeleteTestInput {
    @httpQuery("input1")
    input1: String

    @httpQuery("input2")
    input2: Integer
}

@output
structure DeleteTestOutput {}