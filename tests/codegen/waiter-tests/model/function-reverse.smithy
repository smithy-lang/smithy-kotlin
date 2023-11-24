$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    ReverseFunctionStringListEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "reverse(lists.strings)[0]",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    ReverseFunctionStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "reverse(primitives.string)",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/reverse/{name}", code: 200)
operation GetFunctionReverseEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
