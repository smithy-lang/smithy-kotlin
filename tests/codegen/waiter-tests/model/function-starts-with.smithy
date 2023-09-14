$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    StringStartsWithEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "starts_with(primitives.string, 'foo')",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/starts/{name}", code: 200)
operation GetFunctionStartsWithEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}