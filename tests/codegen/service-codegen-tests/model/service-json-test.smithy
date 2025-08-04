$version: "2.0"

namespace com.json

use aws.protocols#restJson1

@restJson1
@httpBearerAuth
service JsonServiceTest {
    version: "1.0.0"
    operations: [
        HttpHeaderTest
        HttpLabelTest
        HttpQueryTest
        HttpStringPayloadTest
        HttpStructurePayloadTest
        TimestampTest
        JsonNameTest

    ]
}

@http(method: "POST", uri: "/http-header", code: 201)
operation HttpHeaderTest {
    input: HttpHeaderTestInput
    output: HttpHeaderTestOutput
}

@input
structure HttpHeaderTestInput {
    @httpHeader("X-Request-Header")
    header: String

    @httpPrefixHeaders("X-Request-Headers-")
    headers: MapOfStrings
}

@output
structure HttpHeaderTestOutput {
    @httpHeader("X-Response-Header")
    header: String

    @httpPrefixHeaders("X-Response-Headers-")
    headers: MapOfStrings
}


@http(method: "GET", uri: "/http-label/{foo}", code: 200)
operation HttpLabelTest {
    input: HttpLabelTestInput
    output: HttpLabelTestOutput
}

@input
structure HttpLabelTestInput {
    @required
    @httpLabel
    foo: String
}

@output
structure HttpLabelTestOutput {
    output: String
}

@http(method: "DELETE", uri: "/http-query", code: 200)
operation HttpQueryTest {
    input: HttpQueryTestInput
    output: HttpQueryTestOutput
}

@input
structure HttpQueryTestInput {
    @httpQuery("query")
    query: Integer

    @httpQueryParams
    params: MapOfStrings
}

@output
structure HttpQueryTestOutput {
    output: String
}


@http(method: "POST", uri: "/http-payload/string", code: 201)
operation HttpStringPayloadTest {
    input: HttpStringPayloadTestInput
    output: HttpStringPayloadTestOutput
}

@input
structure HttpStringPayloadTestInput {
    @httpPayload
    content: String
}

@output
structure HttpStringPayloadTestOutput {
    @httpPayload
    content: String
}

@http(method: "POST", uri: "/http-payload/structure", code: 201)
operation HttpStructurePayloadTest {
    input: HttpStructurePayloadTestInput
    output: HttpStructurePayloadTestOutput
}

@input
structure HttpStructurePayloadTestInput {
    @httpPayload
    content: HttpStructurePayloadTestStructure
}

@output
structure HttpStructurePayloadTestOutput {
    @httpPayload
    content: HttpStructurePayloadTestStructure
}


@http(method: "POST", uri: "/timestamp", code: 201)
operation TimestampTest {
    input: TimestampTestInput
    output: TimestampTestOutput
}

@input
structure TimestampTestInput {
    default: Timestamp
    @timestampFormat("date-time")
    dateTime: Timestamp
    @timestampFormat("http-date")
    httpDate: Timestamp
    @timestampFormat("epoch-seconds")
    epochSeconds: Timestamp
}

@output
structure TimestampTestOutput {
    default: Timestamp
    @timestampFormat("date-time")
    dateTime: Timestamp
    @timestampFormat("http-date")
    httpDate: Timestamp
    @timestampFormat("epoch-seconds")
    epochSeconds: Timestamp
}

@http(method: "POST", uri: "/json-name", code: 201)
operation JsonNameTest {
    input: JsonNameTestInput
    output: JsonNameTestOutput
}

@input
structure JsonNameTestInput {
    @jsonName("requestName")
    content: String
}

@output
structure JsonNameTestOutput {
    @jsonName("responseName")
    content: String
}

structure HttpStructurePayloadTestStructure {
    content1: String
    content2: Integer
    content3: Float
}


map MapOfStrings {
    key: String
    value: String
}