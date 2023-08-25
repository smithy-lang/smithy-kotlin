$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    StructListStringMultiSelectHash: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "(lists.structs[].{x: primitives.string, y: strings})[0].x",
                        expected: "foo"
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    StructListStringsMultiSelectHash: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "(lists.structs[].{x: primitives.string, y: strings})[1].y",
                        expected: "foo"
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StructListStringsAnyMultiSelectHash: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "(lists.structs[].{x: primitives.string, y: strings})[1].y",
                        expected: "foo"
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    StructListSubStructPrimitivesBooleanMultiSelectHash: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "(lists.structs[].{x: subStructs})[0].x[0].subStructPrimitives.boolean",
                        expected: "true"
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/multi-select-hash/{name}", code: 200)
operation GetMultiSelectHash {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}