$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    FloorFunctionShortEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "floor(primitives.short) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    FloorFunctionIntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "floor(primitives.integer) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    FloorFunctionLongEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "floor(primitives.long) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    FloorFunctionFloatEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "floor(primitives.float) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
    FloorFunctionDoubleEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "floor(primitives.double) == `1`",
                        expected: "false",
                        comparator: "booleanEquals",
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/floor/{name}", code: 200)
operation GetFunctionFloor {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}