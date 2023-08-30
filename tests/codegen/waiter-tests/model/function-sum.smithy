$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    ShortListSumNotEquals: {
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
    IntegerListSumNotEquals: {
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
    LongListSumNotEquals: {
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
    FloatListSumNotEquals: {
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
    DoubleListSumNotEquals: {
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