$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
@deprecated
service Test {
    version: "1.0.0",
    operations: [
        YeOldeOperation,
    ],
}

@deprecated
@http(method: "GET", uri: "/yeOldeOp")
operation YeOldeOperation {
    input: YeOldeOperationRequest,
}

@deprecated
structure YeOldeOperationRequest {
    @deprecated
    @httpQuery("yeOldeParam")
    yeOldParameter: String,
}
