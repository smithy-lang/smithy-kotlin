$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MergeFunctionPrimitivesAndListsEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "merge(primitives, lists).string",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    MergeFunctionOverrideObjectsEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "values(merge(objectOne, objectTwo))",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/merge/{name}", code: 200)
operation GetFunctionMergeEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}