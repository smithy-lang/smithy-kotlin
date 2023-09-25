$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    ToArrayFunctionStringListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "to_array(lists.strings)",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    ToArrayFunctionBooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "to_array(primitives.boolean)[0]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/array/{name}", code: 200)
operation GetFunctionToArrayEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
