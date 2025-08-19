$version: "2.0"

namespace com.cbor

use smithy.protocols#rpcv2Cbor

@rpcv2Cbor
service CborServiceTest {
    version: "1.0.0"
    operations: [
        PostTest
        AuthTest
        ErrorTest
        HttpErrorTest
    ]
}

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

@http(method: "POST", uri: "/error", code: 200)
operation ErrorTest {
    input: ErrorTestInput
    output: ErrorTestOutput
}

@input
structure ErrorTestInput {
    input1: String
}

@output
structure ErrorTestOutput {
    output1: String
}


@http(method: "POST", uri: "/http-error", code: 200)
operation HttpErrorTest {
    input: HttpErrorTestInput
    output: HttpErrorTestOutput
    errors: [HttpError]
}

@input
structure HttpErrorTestInput {}

@output
structure HttpErrorTestOutput {}

@error("client")
@httpError(456)
structure HttpError {
    msg: String
    num: Integer
}