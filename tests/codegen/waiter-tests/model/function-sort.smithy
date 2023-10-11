$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    SortNumberEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sort(lists.integers)[2] == `2`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    SortStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sort(lists.strings)[2]",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/sort/{name}", code: 200)
operation GetFunctionSortEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
