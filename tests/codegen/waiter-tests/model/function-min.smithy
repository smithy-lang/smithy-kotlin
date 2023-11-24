$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MinFunctionShortListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min(lists.shorts) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MinFunctionIntegerListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min(lists.integers) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MinFunctionLongListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min(lists.longs) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MinFunctionFloatListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min(lists.floats) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MinFunctionDoubleListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min(lists.doubles) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MinFunctionStringListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min(lists.strings)",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/min/{name}", code: 200)
operation GetFunctionMinEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
