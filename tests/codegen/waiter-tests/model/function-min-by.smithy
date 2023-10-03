$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MinByNumberEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min_by(lists.structs, &integer).integer == `100`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MinByStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "min_by(lists.structs, &string).string",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/minBy/{name}", code: 200)
operation GetFunctionMinByEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
