$version: "1.0"
namespace com.test

use smithy.waiters#waitable

service Test {
    version: "1.0.0",
    operations: [
        DescribeFoo,
        DescribeFooRequired,
    ]
}

@waitable(
    FooExists: {
        documentation: "Wait until a foo exists",
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
operation DescribeFoo {
    input: DescribeFooInput,
    output: DescribeFooOutput,
    errors: [NotFound, UnknownError]
}

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

structure DescribeFooInput {
    id: String
}

structure DescribeFooOutput {
    name: String
}

structure DescribeFooRequiredInput {
    @required
    id: String
}


@error("client")
structure NotFound {}

@error("server")
structure UnknownError {}
