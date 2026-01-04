$version: "1.0"
namespace com.test

use smithy.waiters#waitable

service Test {
    version: "1.0.0"
    resources: [
        Foo
    ]
}

resource Foo {
    identifiers: { id: String }
    read: DescribeFooRequired
}

@readonly
@waitable(
    FooRequiredExists: {
        documentation: "Wait until a foo exists with required input",
        acceptors: [
            {
                state: "success",
                matcher: {
                    success: true
                }
            },
            {
                state: "retry",
                matcher: {
                    errorType: "NotFound"
                }
            }
        ]
    }
)
operation DescribeFooRequired {
    input: DescribeFooRequiredInput,
    output: DescribeFooOutput,
    errors: [NotFound, UnknownError]
}

structure DescribeFooRequiredInput {
    @required
    id: String
}

structure DescribeFooOutput {
    name: String
}

@error("client")
structure NotFound {}

@error("server")
structure UnknownError {}
