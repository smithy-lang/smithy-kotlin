$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    // list
    BooleanListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.booleans, primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanListContainsIdentityProjection: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.booleans[], primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.strings, primitives.string)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.integers, primitives.integer)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.enums, primitives.enum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumListContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(lists.intEnums, primitives.intEnum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // object projection
    BooleanMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.booleans.*, primitives.boolean)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.strings.*, primitives.string)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.integers.*, primitives.integer)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.enums.*, primitives.enum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumMapContains: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "contains(maps.intEnums.*, primitives.intEnum)",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/contains/{name}", code: 200)
operation GetFunctionContains {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}