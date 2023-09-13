$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    ValuesFunctionSampleValuesEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "values(sampleValues)",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    ValuesFunctionAnySampleValuesEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "values(sampleValues)",
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