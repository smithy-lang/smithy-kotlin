$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    ShortListSumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sum(lists.shorts) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerListSumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sum(lists.integers) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    LongListSumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sum(lists.longs) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    FloatListSumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sum(lists.floats) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    DoubleListSumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sum(lists.doubles) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/sum/{name}", code: 200)
operation GetFunctionSumEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}