$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MaxByNumberEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max_by(lists.structs, &integer).integer == `100`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    MaxByStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "max_by(lists.structs, &string).string",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/maxBy/{name}", code: 200)
operation GetFunctionMaxByEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
