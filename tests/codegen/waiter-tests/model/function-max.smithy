$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MaxFunctionShortListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max(lists.shorts) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MaxFunctionIntegerListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max(lists.integers) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MaxFunctionLongListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max(lists.longs) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MaxFunctionFloatListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max(lists.floats) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MaxFunctionDoubleListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max(lists.doubles) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MaxFunctionStringListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max(lists.strings)",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/max/{name}", code: 200)
operation GetFunctionMaxEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
