$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    ToStringFunctionStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "to_string(primitives.string)",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    ToStringFunctionBooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "to_string(primitives.boolean)",
                        expected: "true",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/toString/{name}", code: 200)
operation GetFunctionToStringEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
