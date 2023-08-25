$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    CeilFunctionShortEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "ceil(primitives.short) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    CeilFunctionIntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "ceil(primitives.integer) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    CeilFunctionLongEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "ceil(primitives.long) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    CeilFunctionFloatEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "ceil(primitives.float) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    CeilFunctionDoubleEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "ceil(primitives.double) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/ceil/{name}", code: 200)
operation GetFunctionCeil {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}