$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    StringListJoinEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "join('', lists.strings)",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    StringListSeparatorJoinEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "join(', ', lists.strings)",
                        expected: "foo, bar",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/join/{name}", code: 200)
operation GetFunctionJoinEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}