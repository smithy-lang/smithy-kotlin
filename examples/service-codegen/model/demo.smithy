// model/greeter.smithy
$version: "2.0"

namespace com.demo

use aws.protocols#restJson1
use smithy.api#httpBearerAuth

@restJson1
@httpBearerAuth
service DemoService {
    version: "1.0.0"
    operations: [
        SayHello
    ]
}

@http(method: "POST", uri: "/greet", code: 201)
operation SayHello {
    input: SayHelloInput
    output: SayHelloOutput
    errors: [
        CustomError
    ]
}

@input
structure SayHelloInput {
    @required
    @length(min: 3, max: 10)
    name: String

    @httpHeader("X-User-ID")
    id: Integer
}

@output
structure SayHelloOutput {
    greeting: String
}

@error("server")
@httpError(500)
structure CustomError {
    msg: String

    @httpHeader("X-User-error")
    err: String
}
