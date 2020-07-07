$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
service Example {
    version: "1.0.0",
    operations: [
        GetFoo,
        GetFooNoInput,
        GetFooNoOutput,
        GetFooStreamingInput,
        GetFooStreamingOutput,
        GetFooStreamingOutputNoInput,
        GetFooStreamingInputNoOutput
    ]
}

@http(method: "GET", uri: "/foo")
operation GetFoo {
    input: GetFooRequest,
    output: GetFooResponse,
    errors: [GetFooError]
}

structure GetFooRequest {}
structure GetFooResponse {}

@error("client")
structure GetFooError {}


@http(method: "GET", uri: "/foo-no-input")
operation GetFooNoInput {
    output: GetFooResponse
}

@http(method: "GET", uri: "/foo-no-output")
operation GetFooNoOutput {
    input: GetFooRequest
}

@streaming
blob BodyStream

structure GetFooStreamingRequest {
    body: BodyStream
}

structure GetFooStreamingResponse {
    body: BodyStream
}

@http(method: "POST", uri: "/foo-streaming-input")
operation GetFooStreamingInput {
    input: GetFooStreamingRequest,
    output: GetFooResponse
}

@http(method: "POST", uri: "/foo-streaming-output")
operation GetFooStreamingOutput {
    input: GetFooRequest,
    output: GetFooStreamingResponse
}

@http(method: "POST", uri: "/foo-streaming-output-no-input")
operation GetFooStreamingOutputNoInput {
    output: GetFooStreamingResponse
}

@http(method: "POST", uri: "/foo-streaming-input-no-output")
operation GetFooStreamingInputNoOutput {
    input: GetFooStreamingRequest
}
