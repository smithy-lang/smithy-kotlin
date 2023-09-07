$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    KeysFunctionPrimitivesStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "keys(primitives)",
                        expected: "string",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    KeysFunctionPrimitivesIntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "keys(primitives)",
                        expected: "integer",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/keys/{name}", code: 200)
operation GetFunctionKeysEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}