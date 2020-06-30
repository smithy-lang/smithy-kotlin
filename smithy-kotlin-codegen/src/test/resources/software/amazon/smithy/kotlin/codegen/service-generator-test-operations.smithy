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

operation GetFoo {
    input: GetFooRequest,
    output: GetFooResponse,
    errors: [GetFooError]
}

structure GetFooRequest {}
structure GetFooResponse {}

@error("client")
structure GetFooError {}


operation GetFooNoInput {
    output: GetFooResponse
}

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

operation GetFooStreamingInput {
    input: GetFooStreamingRequest,
    output: GetFooResponse
}

operation GetFooStreamingOutput {
    input: GetFooRequest,
    output: GetFooStreamingResponse
}

operation GetFooStreamingOutputNoInput {
    output: GetFooStreamingResponse
}

operation GetFooStreamingInputNoOutput {
    input: GetFooStreamingRequest
}
