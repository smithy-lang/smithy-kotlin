namespace com.test

use aws.protocols#restJson1
use smithy.waiters#waitable

service WaitersTestService {
    operations: [GetEntity]
}

@waitable(
    EntityExistsBySuccess: {
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
    EntityHasNameTagByOutput: {
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
    },
    EntityHasComparableNumericalValues: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "size == `42`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    }
)
@readonly
@http(method: "GET", uri: "/entities/{name}", code: 200)
operation GetEntity {
    input: GetEntityRequest,
    output: GetEntityResponse
}

structure GetEntityRequest {
    @required
    @httpLabel
    name: String
}

structure GetEntityResponse {
    name: String,
    tags: Tags,
    size: Integer
}

map Tags {
    key: String,
    value: String
}
