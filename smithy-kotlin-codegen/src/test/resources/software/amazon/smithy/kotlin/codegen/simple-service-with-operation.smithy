$version: "1.0"
namespace smithy.example

use aws.protocols#awsJson1_1

@awsJson1_1
service Example {
    version: "1.0.0",
    operations: [GetFoo]
}

operation GetFoo {
    input: GetFooInput,
    output: GetFooOutput,
    errors: [GetFooError]
}

structure GetFooInput {
    bigInt: BigInteger
}
structure GetFooOutput {}

@error("client")
structure GetFooError {}
