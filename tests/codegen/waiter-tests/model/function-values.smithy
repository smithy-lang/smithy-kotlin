$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"]) // TODO: Remove
@waitable(
    KeysFunctionPrimitivesIntegerEquals2: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "values(primitives)[0]",
                        expected: "hello",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/values/{name}", code: 200)
operation GetFunctionValuesEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}