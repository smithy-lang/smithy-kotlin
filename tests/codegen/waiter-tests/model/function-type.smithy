$version: "2"
namespace com.test

use smithy.waiters#waitable

@suppress(["WaitableTraitJmespathProblem"])
@waitable(
    TypeFunctionStringEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.string)",
                        expected: "string",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionBooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.boolean)",
                        expected: "boolean",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionArrayEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(lists.booleans)",
                        expected: "array",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionShortEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.short)",
                        expected: "number",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionIntegerEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.integer)",
                        expected: "number",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionLongEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.long)",
                        expected: "number",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionFloatEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.float)",
                        expected: "number",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionDoubleEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.double)",
                        expected: "number",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionObjectEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives)",
                        expected: "object",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionMergedObjectEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(merge(primitives, primitives))",
                        expected: "object",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    TypeFunctionNullEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "type(primitives.boolean)",
                        expected: "null",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/type/{name}", code: 200)
operation GetFunctionTypeEquals {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}
