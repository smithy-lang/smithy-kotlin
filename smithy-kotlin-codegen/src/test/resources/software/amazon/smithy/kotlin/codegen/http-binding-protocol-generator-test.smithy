$version: "1.0"
namespace com.test

use aws.protocols#awsJson1_1

@awsJson1_1
service Example {
    version: "1.0.0",
    operations: [
        SmokeTest,
        DuplicateInputTest,
        ExplicitString,
        ExplicitBlob,
        ExplicitBlobStream,
        ExplicitStruct,
        ListInput,
        MapInput,
        EnumInput,
        TimestampInput,
        BlobInput,
        ConstantQueryString,
        PrefixHeaders
    ]
}

@http(method: "POST", uri: "/smoketest/{label1}/foo")
operation SmokeTest {
    input: SmokeTestRequest,
    output: SmokeTestResponse,
    errors: [SmokeTestError]
}

@http(method: "POST", uri: "/smoketest-duplicate/{label1}/foo")
operation DuplicateInputTest {
    // uses the same input type as another operation. Ensure that we only generate one instance of the serializer
    input: SmokeTestRequest
}

structure SmokeTestRequest {
    @httpHeader("X-Header1")
    header1: String,

    @httpHeader("X-Header2")
    header2: String,

    @httpQuery("Query1")
    query1: String,

    @required
    @httpLabel
    label1: String,

    payload1: String,
    payload2: Integer,
    payload3: Nested
}

structure Nested {
    member1: String,
    member2: String
}

structure SmokeTestResponse {

    @httpHeader("X-Header1")
    strHeader: String,

    @httpHeader("X-Header2")
    intHeader: Integer,

    @httpHeader("X-Header3")
    tsListHeader: TimestampList,

    payload1: String,
    payload2: Integer,
    payload3: Nested,
    @timestampFormat("date-time")
    payload4: Timestamp
}

@error("client")
structure SmokeTestError {}


@http(method: "POST", uri: "/explicit/string")
operation ExplicitString {
    input: ExplicitStringRequest,
    output: ExplicitStringResponse
}

structure ExplicitStringRequest {
    @httpPayload
    payload1: String
}

structure ExplicitStringResponse {
    @httpPayload
    payload1: String
}

@http(method: "POST", uri: "/explicit/blob")
operation ExplicitBlob {
    input: ExplicitBlobRequest,
    output: ExplicitBlobResponse
}

structure ExplicitBlobRequest {
    @httpPayload
    payload1: Blob
}

structure ExplicitBlobResponse {
    @httpPayload
    payload1: Blob
}

@streaming
blob BodyStream

@http(method: "POST", uri: "/explicit/blobstream")
operation ExplicitBlobStream {
    input: ExplicitBlobStreamRequest,
    output: ExplicitBlobStreamResponse
}

structure ExplicitBlobStreamRequest {
    @httpPayload
    payload1: BodyStream
}

structure ExplicitBlobStreamResponse {
    @httpPayload
    payload1: BodyStream
}

@http(method: "POST", uri: "/explicit/struct")
operation ExplicitStruct {
    input: ExplicitStructRequest,
    output: ExplicitStructResponse
}

structure Nested4 {
    member1: Integer,
    // sanity check, member serialization for non top-level (bound to the operation input) aggregate shapes
    intList: IntList,
    intMap: IntMap
}

structure Nested3 {
    member1: String,
    member2: String,
    member3: Nested4
}

structure Nested2 {
    moreNesting: Nested3
}

structure ExplicitStructRequest {
    @httpPayload
    payload1: Nested2
}

structure ExplicitStructResponse {
    @httpPayload
    payload1: Nested2
}

list IntList {
    member: Integer
}

list StructList {
    member: Nested
}

// A list of lists of integers
list NestedIntList {
    member: IntList
}

// A list of enums
list EnumList {
    member: MyEnum
}

list BlobList {
    member: Blob
}

@http(method: "POST", uri: "/input/list")
operation ListInput {
    input: ListInputRequest,
    output: ListOutputResponse
}

structure ListInputRequest {
    enumList: EnumList,
    intList: IntList,
    structList: StructList,
    nestedIntList: NestedIntList,
    blobList: BlobList
}

structure ListOutputResponse {
    enumList: EnumList,
    intList: IntList,
    structList: StructList,
    nestedIntList: NestedIntList,
    blobList: BlobList
}

map IntMap {
    key: String,
    value: Integer
}

map StringMap {
    key: String,
    value: String
}

// only exists as value of a map through MapInputRequest::structMap
structure ReachableOnlyThroughMap {
    prop1: Integer
}

map StructMap {
    key: String,
    value: ReachableOnlyThroughMap
}

map EnumMap {
    key: String,
    value: MyEnum
}

map BlobMap {
    key: String,
    value: Blob
}

map NestedMap {
    key: String,
    value: IntMap
}

@http(method: "POST", uri: "/input/map")
operation MapInput {
    input: MapInputRequest,
    output: MapOutputResponse
}

structure MapInputRequest {
    intMap: IntMap,
    structMap: StructMap,
    enumMap: EnumMap,
    blobMap: BlobMap
}

structure MapOutputResponse {
    intMap: IntMap,
    structMap: StructMap,
    enumMap: EnumMap,
    blobMap: BlobMap,
    nestedMap: NestedMap
}


@http(method: "POST", uri: "/input/enum")
operation EnumInput {
    input: EnumInputRequest
}

@enum([
    {
        value: "rawValue1",
        name: "Variant1"
    },
    {
        value: "rawValue2",
        name: "Variant2"
    }
])
string MyEnum

structure NestedEnum {
    myEnum: MyEnum
}

structure EnumInputRequest {
    nestedWithEnum: NestedEnum,

    @httpHeader("X-EnumHeader")
    enumHeader: MyEnum
}

@http(method: "POST", uri: "/input/timestamp/{tsLabel}")
operation TimestampInput {
    input: TimestampInputRequest
}

list TimestampList {
    member: Timestamp
}

structure TimestampInputRequest {
    // (protocol default)
    normal: Timestamp,

    @timestampFormat("date-time")
    dateTime: Timestamp,

    @timestampFormat("epoch-seconds")
    epochSeconds: Timestamp,

    @timestampFormat("http-date")
    httpDate: Timestamp,

    timestampList: TimestampList,

    @httpHeader("X-Date")
    @timestampFormat("http-date")
    headerHttpDate: Timestamp,

    @httpHeader("X-Epoch")
    @timestampFormat("epoch-seconds")
    headerEpoch: Timestamp,

    @httpQuery("qtime")
    @timestampFormat("date-time")
    queryTimestamp: Timestamp,

    @httpQuery("qtimeList")
    queryTimestampList: TimestampList,

    @required
    @httpLabel
    tsLabel: Timestamp
}

@http(method: "POST", uri: "/input/blob")
operation BlobInput {
    input: BlobInputRequest
}

@mediaType("video/quicktime")
string MyMediaHeader

structure BlobInputRequest {
    // smithy spec doesn't allow blobs for headers but strings with media type are also base64 encoded
    @httpHeader("X-Blob")
    headerMediaType: MyMediaHeader,

    @httpQuery("qblob")
    queryBlob: Blob,

    payloadBlob: Blob
}

@readonly
@http(uri: "/ConstantQueryString/{hello}?foo=bar&hello", method: "GET")
operation ConstantQueryString {
    input: ConstantQueryStringInput
}

structure ConstantQueryStringInput {
    @httpLabel
    @required
    hello: String,
}

@http(method: "POST", uri: "/prefix-headers")
operation PrefixHeaders{
    input: PrefixHeadersIO,
    output: PrefixHeadersIO
}

// input/output with httpPrefixHeaders
structure PrefixHeadersIO {
    @httpPrefixHeaders("X-Foo-")
    member1: StringMap
}
