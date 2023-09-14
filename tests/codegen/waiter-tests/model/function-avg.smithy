$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    EmptyIntegerListAvgNotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "avg(lists.integers) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    ShortListAvgNotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "avg(lists.shorts) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerListAvgNotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "avg(lists.integers) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    LongListAvgNotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "avg(lists.longs) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    FloatListAvgNotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "avg(lists.floats) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    DoubleListAvgNotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "avg(lists.doubles) == `10`",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/avg/{name}", code: 200)
operation GetFunctionAvgEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}