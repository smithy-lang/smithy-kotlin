namespace smithy.kotlin.traits

use aws.protocols#restJson1

@trait(selector: "*")
string paginationEndBehavior

service Lambda {
    operations: [ListFunctions, TruncatedListFunctions, IdenticalTokenListFunctions]
}

// ListFunctions shapes

@paginated(
    inputToken: "Marker",
    outputToken: "NextMarker",
    pageSize: "MaxItems",
    items: "Functions"
)
@readonly
@http(method: "GET", uri: "/functions", code: 200)
operation ListFunctions {
    input: ListFunctionsRequest,
    output: ListFunctionsResponse
}

structure ListFunctionsRequest {
    @httpQuery("FunctionVersion")
    FunctionVersion: String,
    @httpQuery("Marker")
    Marker: String,
    @httpQuery("MasterRegion")
    MasterRegion: String,
    @httpQuery("MaxItems")
    MaxItems: Integer
}

structure ListFunctionsResponse {
    Functions: FunctionConfigurationList,
    NextMarker: String
}

// TruncatedListFunctions shapes

@paginated(
    inputToken: "Marker",
    outputToken: "NextMarker",
    pageSize: "MaxItems",
    items: "Functions"
)
@paginationEndBehavior("TruncationMember:IsTruncated")
@readonly
@http(method: "GET", uri: "/truncatedFunctions", code: 200)
operation TruncatedListFunctions {
    input: TruncatedListFunctionsRequest,
    output: TruncatedListFunctionsResponse
}

structure TruncatedListFunctionsRequest {
    @httpQuery("FunctionVersion")
    FunctionVersion: String,
    @httpQuery("Marker")
    Marker: String,
    @httpQuery("MasterRegion")
    MasterRegion: String,
    @httpQuery("MaxItems")
    MaxItems: Integer
}

structure TruncatedListFunctionsResponse {
    Functions: FunctionConfigurationList,
    IsTruncated: Boolean,
    NextMarker: String
}

// IdenticalTokenListFunctions shapes

@paginated(
    inputToken: "Marker",
    outputToken: "NextMarker",
    pageSize: "MaxItems",
    items: "Functions"
)
@paginationEndBehavior("IdenticalToken")
@readonly
@http(method: "GET", uri: "/identicalTokenFunctions", code: 200)
operation IdenticalTokenListFunctions {
    input: IdenticalTokenListFunctionsRequest,
    output: IdenticalTokenListFunctionsResponse
}

structure IdenticalTokenListFunctionsRequest {
    @httpQuery("FunctionVersion")
    FunctionVersion: String,
    @httpQuery("Marker")
    Marker: String,
    @httpQuery("MasterRegion")
    MasterRegion: String,
    @httpQuery("MaxItems")
    MaxItems: Integer
}

structure IdenticalTokenListFunctionsResponse {
    Functions: FunctionConfigurationList,
    NextMarker: String
}

// Common shapes

list FunctionConfigurationList {
    member: FunctionConfiguration
}

structure FunctionConfiguration {
    FunctionName: String
}
