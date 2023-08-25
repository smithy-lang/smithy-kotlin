$version: "2"
namespace com.test

use smithy.waiters#waitable

@waitable(
    // list indexing
    BooleanListIndexZeroEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[0]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanListIndexOneEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[1]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    BooleanListIndexNegativeTwoEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.booleans[-2]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    TwoDimensionalBooleanListIndexZeroZeroEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "twoDimensionalLists.booleansList[0][0]",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
        ]
    },
    StructListIndexOneStringsIndexZeroEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[1].strings[0]",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
      ]
    },
    StructListIndexOneSubStructsIndexZeroSubStructPrimitivesBooleanEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.structs[1].subStructs[0].subStructPrimitives.boolean",
                        expected: "true",
                        comparator: "booleanEquals"
                    }
                }
            }
      ]
    },

    // list slicing
    StringListStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[::2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[:2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[2:]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStopStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[:4:2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[2::3]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[3:4]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartStopStepSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[2:4:2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListNegativeStartStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[-2:-1]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStartNegativeStopSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[1:-2]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
    StringListStopNegativeStartSlicingEquals: {
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "lists.strings[-3:3]",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            }
        ]
    },
)
@readonly
@http(method: "GET", uri: "/list/{name}", code: 200)
operation GetListOperation {
    input: GetEntityRequest,
    output: GetEntityResponse,
    errors: [NotFound],
}