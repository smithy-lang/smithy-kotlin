namespace smithy.kotlin.traits

use aws.protocols#restJson1

@trait(selector: "*")
structure paginationTruncationMember { }

service Lambda {
    operations: [ListFunctions, TruncatedListFunctions]
}

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

@paginated(
    inputToken: "Marker",
    outputToken: "NextMarker",
    pageSize: "MaxItems",
    items: "Functions"
)
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
    @paginationTruncationMember
    IsTruncated: Boolean,
    NextMarker: String
}

list FunctionConfigurationList {
    member: FunctionConfiguration
}

structure FunctionConfiguration {
    FunctionName: String
}
