$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    ValuesFunctionPrimitivesEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "values(primitives)",
                        expected: "foo",
                        comparator: "anyStringEquals"
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