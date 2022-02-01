namespace com.test

use aws.protocols#restJson1
use smithy.waiters#waitable

service Lambda {
    operations: [GetFunction]
}

@waitable(
    FunctionExistsBySuccess: {
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
    },
    FunctionHasNameTagByOutput: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(tags.*, name)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            },
            {
                state: "retry",
                matcher: {
                    output: {
                        path: "contains(tags.*, name)",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    }
)
@readonly
@http(method: "GET", uri: "/functions/{name}", code: 200)
operation GetFunction {
    input: GetFunctionRequest,
    output: GetFunctionResponse
}

structure GetFunctionRequest {
    @required
    @httpLabel
    name: String
}

structure GetFunctionResponse {
    name: String,
    tags: Tags
}

map Tags {
    key: String,
    value: String
}
