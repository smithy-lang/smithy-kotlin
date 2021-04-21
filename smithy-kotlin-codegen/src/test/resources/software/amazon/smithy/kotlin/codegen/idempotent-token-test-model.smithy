$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
service Test {
    version: "1.0.0",
    operations: [
        AllocateWidget,
        AllocateWidgetQuery,
        AllocateWidgetHeader
    ]
}

// https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#idempotencytoken-trait
@http(method: "POST", uri: "/input/AllocateWidget")
operation AllocateWidget {
    input: AllocateWidgetInput
}

structure AllocateWidgetInput {
    @idempotencyToken
    clientToken: String
}

@http(method: "POST", uri: "/input/AllocateWidgetQuery")
operation AllocateWidgetQuery {
    input: AllocateWidgetInputQuery
}

structure AllocateWidgetInputQuery {
    @httpQuery("clientToken")
    @idempotencyToken
    clientToken: String
}

@http(method: "POST", uri: "/input/AllocateWidgetHeader")
operation AllocateWidgetHeader {
    input: AllocateWidgetInputHeader
}

structure AllocateWidgetInputHeader {
    @httpHeader("clientToken")
    @idempotencyToken
    clientToken: String
}