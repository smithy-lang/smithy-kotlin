$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    SortByNumberEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sort_by(lists.structs, &integer)[0].integer == `1`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    SortByStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "sort_by(lists.structs, &string)[2].string",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/sortBy/{name}", code: 200)
operation GetFunctionSortByEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
