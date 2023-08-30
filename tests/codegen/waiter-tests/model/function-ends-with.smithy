$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    StringEndsWithEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "ends_with(primitives.string, 'baz')",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/ends/{name}", code: 200)
operation GetFunctionEndsWithEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}