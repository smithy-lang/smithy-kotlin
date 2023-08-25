$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    // list
    BooleanListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.booleans) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.strings) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.integers) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.enums) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumListLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.intEnums) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // object projection
    BooleanMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.booleans.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.strings.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.integers.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.enums.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumMapLength: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(maps.intEnums.*) > `0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },

    // function: length, list filter
    // TODO: @ requires generic support for CurrentExpression, currently only recognized within flattens
    //BooleanListLengthFiltered: {
    //    acceptors: [
    //        {
    //            state: "success",
    //            matcher: {
    //                output: {
    //                    path: "length(lists.booleans[?@ == `true`]) > `0`"
    //                    expected: "true",
    //                    comparator: "booleanEquals"
    //                }
    //            }
    //        }
    //    ]
    //},

    // compound filter
    HasStructWithBoolean: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.boolean == `true`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithString: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.string == `\"foo\"`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithInteger: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.integer == `1`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithEnum: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.enum == `\"one\"`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithIntEnum: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?primitives.intEnum == `1`]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithStringInStringList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(strings, primitives.string)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithEnumInEnumList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(enums, primitives.enum)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithStringInEnumList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(enums, primitives.string)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    HasStructWithEnumInStringList: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "length(lists.structs[?contains(strings, primitives.enum)]) > `0`"
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/length/{name}", code: 200)
operation GetFunctionLength {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}