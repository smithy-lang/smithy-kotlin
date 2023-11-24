$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    MergeFunctionOverrideObjectsOneEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "merge(objectOne, objectTwo).valueOne",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    MergeFunctionOverrideObjectsTwoEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "merge(objectOne, objectTwo).valueTwo",
                        expected: "bar",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    MergeFunctionOverrideObjectsThreeEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "merge(objectOne, objectTwo).valueThree",
                        expected: "baz",
                        comparator: "stringEquals"
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