$version: "1.0"
namespace com.test

use smithy.waiters#waitable

service Test {
    version: "1.0.0",
    operations: [DescribeFoo]
}

@waitable(
    DefaultDelays: {
        documentation: "Test default delays",
        acceptors: [
            {
                state: "success",
                matcher: {
                    success: true
                }
            },
            {
                state: "retry",
                matcher: {
                    errorType: "RetryError"
                }
            }
        ]
    },
    CustomDelays: {
        documentation: "Test custom delays",
        acceptors: [
            {
                state: "success",
                matcher: {
                    errorType: "SuccessError"
                }
            }
        ],
        minDelay: 5,
        maxDelay: 30
    },
    SuccessAcceptors: {
        documentation: "Test default delays",
        acceptors: [
            {
                state: "success",
                matcher: {
                    success: true
                }
            },
            {
                state: "failure",
                matcher: {
                    success: false
                }
            }
        ]
    },
    ErrorTypeAcceptors: {
        documentation: "Test errors",
        acceptors: [
            {
                state: "success",
                matcher: {
                    errorType: "SuccessError"
                }
            },
            {
                state: "retry",
                matcher: {
                    errorType: "RetryError"
                }
            },
            {
                state: "failure",
                matcher: {
                    errorType: "FailureError"
                }
            }
        ]
    },
    OutputAcceptor: {
        documentation: "Test an output path acceptor",
        acceptors: [
            {
                state: "success",
                matcher: {
                    output: {
                        path: "name",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            }
        ]
    },
    InputOutputAcceptors: {
        documentation: "Test an output path acceptor",
        acceptors: [
            {
                state: "success",
                matcher: {
                    inputOutput: {
                        path: "input.id",
                        expected: "foo",
                        comparator: "stringEquals"
                    }
                }
            },
            {
                state: "success",
                matcher: {
                    inputOutput: {
                        path: "output.isDeprecated",
                        expected: "false",
                        comparator: "booleanEquals"
                    }
                }
            },
            {
                state: "success",
                matcher: {
                    inputOutput: {
                        path: "output.tags",
                        expected: "foo",
                        comparator: "allStringEquals"
                    }
                }
            },
            {
                state: "success",
                matcher: {
                    inputOutput: {
                        path: "output.tags",
                        expected: "foo",
                        comparator: "anyStringEquals"
                    }
                }
            }
        ]
    }
)
operation DescribeFoo {
    input: DescribeFooInput,
    output: DescribeFooOutput,
    errors: [SuccessError, FailureError, RetryError, UnknownError]
}

structure DescribeFooInput {
    id: String
}

structure DescribeFooOutput {
    name: String,
    isDeprecated: Boolean,
    tags: StringList,
    foo: Foo
}

list StringList {
    member: String
}

structure Foo {
    bar: Bar
}

structure Bar {
    baz: String
}

@error("client")
structure SuccessError {}

@error("client")
structure FailureError {}

@error("client")
structure RetryError {}

@error("server")
structure UnknownError {}
