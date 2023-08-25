$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    HasStructWithStringByProjection: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].primitives.string"
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    HasStructWithSubstructWithStringByProjection: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].subStructs[].subStructPrimitives.string"
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
    HasFilteredSubStruct: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[].subStructs[?subStructPrimitives.integer > `0`][].subStructPrimitives.string"
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/sub-field-projection/{name}", code: 200)
operation GetSubFieldProjection {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}