$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    StructListStringMultiSelectList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].[primitives.string][0][0]",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    StructListStringListMultiSelectList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].[primitives.string, primitives.string][1]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StructListSubStructPrimitivesBooleanMultiSelectList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].[subStructs][1][0][0].subStructPrimitives.boolean",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/multi-select-list/{name}", code: 200)
operation GetMultiSelectList {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}