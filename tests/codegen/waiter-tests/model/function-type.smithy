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
                        path: "type(primitives.string) == types.string",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.boolean) == types.boolean",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(lists.booleans) == types.array",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.short) == types.number",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.integer) == types.number",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.long) == types.number",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.float) == types.number",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.double) == types.number",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives) == types.objectType",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(merge(primitives, primitives)) == types.objectType",
                        expected: "true",
                        comparator: "booleanEquals"
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
                        path: "type(primitives.boolean) == types.nullType",
                        expected: "true",
                        comparator: "booleanEquals"
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
