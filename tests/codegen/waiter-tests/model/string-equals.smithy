$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    // anyStringEquals
    StringListAnyStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings",
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    EnumListAnyStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.enums",
                        expected: "one",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },

    // allStringEquals
    StringListAllStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    EnumListAllStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.enums",
                        expected: "one",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/string/{name}", code: 200)
operation GetStringEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}