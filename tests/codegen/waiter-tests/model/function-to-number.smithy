$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    ToNumberFunctionStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "to_number(primitives.string) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    ToNumberFunctionIntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "to_number(primitives.integer) == `10`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/toNumber/{name}", code: 200)
operation GetFunctionToNumberEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
