$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    AbsFunctionShortEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "abs(primitives.short) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    AbsFunctionIntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "abs(primitives.integer) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    AbsFunctionLongEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "abs(primitives.long) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    AbsFunctionFloatEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "abs(primitives.float) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    AbsFunctionDoubleEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "abs(primitives.double) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/abs/{name}", code: 200)
operation GetFunctionAbs {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}