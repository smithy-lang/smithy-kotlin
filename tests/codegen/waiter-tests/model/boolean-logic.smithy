$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    AndEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[0] && lists.booleans[1]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    OrEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[0] || lists.booleans[1]",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    NotEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "!(primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/boolean/{name}", code: 200)
operation GetBooleanLogic {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}