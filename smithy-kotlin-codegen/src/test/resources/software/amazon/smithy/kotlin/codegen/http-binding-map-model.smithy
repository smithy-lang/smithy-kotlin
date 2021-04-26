$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
service Test {
    version: "1.0.0",
    operations: [MapInput]
}

list IntList {
    member: Integer
}

map MapOfLists {
    key: String,
    value: IntList
}

@http(method: "POST", uri: "/input/map")
operation MapInput {
    input: MapInputRequest
}

structure MapInputRequest {
    mapOfLists: MapOfLists
}
