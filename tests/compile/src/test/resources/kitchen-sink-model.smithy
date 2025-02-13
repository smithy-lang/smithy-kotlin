$version: "1.0"
namespace com.test

use aws.protocols#restJson1
use smithy.waiters#waitable

@restJson1
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
        PrefixHeaders,
        Primitives,
        UnionInput,
        UnionAggregateInput,
        UnionOutput,
        UnionAggregateOutput,
        WaiterTest
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
structure SmokeTestError {
    nested: NestedErrorData
}


structure NestedErrorData {
    field1: Integer
}


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

list NestedMapList {
    member: IntMap
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
    nestedMapList: NestedMapList,
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

map EnumKeyedStringMap {
    key: MyEnum,
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

map MapOfLists {
    key: String,
    value: IntList
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
    blobMap: BlobMap,
    stringMap: StringMap,
    enumKeyedStringMap: EnumKeyedStringMap,
    mapOfLists: MapOfLists,
    nestedMap: NestedMap
}

structure MapOutputResponse {
    intMap: IntMap,
    structMap: StructMap,
    enumMap: EnumMap,
    blobMap: BlobMap,
    stringMap: StringMap,
    enumKeyedStringMap: EnumKeyedStringMap,
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

@http(method: "POST", uri: "/primitives")
operation Primitives {
    input: PrimitivesIO,
    output: PrimitivesIO
}

// A structure containing all the primitive types
structure PrimitivesIO {
    bigDecimal: BigDecimal,
    bigInteger: BigInteger,
    boolean: Boolean,
    byte: Byte,
    double: Double,
    float: Float,
    integer: Integer,
    long: Long,
    short: Short,
    string: String,
    timestamp: Timestamp,
    primitiveBoolean: PrimitiveBoolean,
    primitiveByte: PrimitiveByte,
    primitiveShort: PrimitiveShort,
    primitiveInteger: PrimitiveInteger,
    primitiveLong: PrimitiveLong,
    primitiveFloat: PrimitiveFloat,
    primitiveDouble: PrimitiveDouble
}

@http(method: "POST", uri: "/input/union")
operation UnionInput {
    input: UnionRequest
}

@http(method: "GET", uri: "/input/union")
operation UnionOutput {
    output: UnionRequest
}

structure UnionRequest {
    payloadUnion: MyUnion
}

union MyUnion {
    i32: Integer,
    stringA: String
}

@http(method: "POST", uri: "/input/union2")
operation UnionAggregateInput {
    input: UnionAggregateRequest
}

@http(method: "GET", uri: "/input/union2")
operation UnionAggregateOutput {
    output: UnionAggregateRequest
}

structure UnionAggregateRequest {
    payloadAggregateUnion: MyAggregateUnion
}

union MyAggregateUnion {
    i32: Integer,
    intList: IntList,
    intMap: IntMap,
    nested3: Nested,
    @timestampFormat("date-time")
    timestamp4: Timestamp
}

@http(method: "GET", uri: "/waitertest/{id}")
@waitable(
    WaiterExists: {
        documentation: "Wait until something exists",
        acceptors: [
            {
                state: "failure",
                matcher: {
                    output: {
                        path: "foo.bar",
                        expected: "baz",
                        comparator: "stringEquals"
                    }
                }
            },
            {
                state: "success",
                matcher: {
                    success: true
                }
            },
            {
                state: "retry",
                matcher: {
                    errorType: "WaiterTestNotFound"
                }
            }
        ]
    }
)
operation WaiterTest {
    input: WaiterTestInput,
    output: WaiterTestOutput,
    errors: [WaiterTestNotFound]
}

structure WaiterTestInput {
    @httpLabel
    @required
    id: String
}

structure WaiterTestOutput {
    foo: WaiterTestFoo
}

structure WaiterTestFoo {
    bar: String
}

@error("client")
structure WaiterTestNotFound {}
