$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    BooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.boolean",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanEqualsByCompare: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.boolean == `true`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.string",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    StringEqualsByCompare: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.string == `\"foo\"`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    ByteEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.byte == `1`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    ShortEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.short == `2`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.integer == `3`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    LongEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.long == `4`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    FloatEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.float == `5.0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    DoubleEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.double == `6.0`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    EnumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.enum",
                        expected: "one",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    EnumEqualsByCompare: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.enum == `\"one\"`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    IntEnumEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "primitives.intEnum == `1`",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/primitive/{name}", code: 200)
operation GetPrimitive {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}