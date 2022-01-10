namespace com.test

use aws.protocols#restJson1

service Lambda {
    operations: [ListFunctions]
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

list FunctionConfigurationList {
    member: FunctionConfiguration
}

structure FunctionConfiguration {
    FunctionName: String
}
