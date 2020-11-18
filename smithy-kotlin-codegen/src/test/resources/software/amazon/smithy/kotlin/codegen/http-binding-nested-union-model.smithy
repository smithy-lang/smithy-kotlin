$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
service Example {
    version: "1.0.0",
    operations: [UnionTestOperation]
}

@http(method: "GET", uri: "/input/union2")
operation UnionTestOperation {
    output: NestedListResponse
}

structure NestedListResponse {
    payloadAggregateUnion: MyAggregateUnion
}

list IntList {
    member: Integer
}

list ListOfIntList {
    member: IntList
}

map MapOfLists {
    key: String,
    value: IntList
}

union MyAggregateUnion {
    i32: Integer,
    intList: IntList,
    listOfIntList: ListOfIntList,
    mapOfLists: MapOfLists
}
