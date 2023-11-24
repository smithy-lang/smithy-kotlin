$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    NotNullFunctionStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "not_null(lists.strings[0], primitives.string, primitives.integer)",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/null/{name}", code: 200)
operation GetFunctionNotNullEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
