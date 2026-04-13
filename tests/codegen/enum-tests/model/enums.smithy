$version: "2.0"
namespace smithy.kotlin.enums

use aws.protocols#awsJson1_0

@awsJson1_0
service EnumTestService {
    operations: [DoSomething],
    version: "1"
}

operation DoSomething {
    input: DoSomethingInput
}

@input
structure DoSomethingInput {
    source: ContentSource
}

enum ContentSource {
    INPUT
    OUTPUT
}
