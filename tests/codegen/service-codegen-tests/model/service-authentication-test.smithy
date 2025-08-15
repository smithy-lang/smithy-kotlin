$version: "2.0"

namespace com.authentication

use smithy.api#httpBearerAuth
use aws.auth#sigv4
use aws.auth#sigv4a
use aws.protocols#restJson1

@restJson1
@auth([sigv4a, sigv4, httpBearerAuth])
@httpBearerAuth
@sigv4(name: "service-1")
@sigv4a(name: "service-1")
service AuthenticationServiceTest {
    version: "1.0.0"
    operations: [
        OnlyBearerTest
        OnlySigV4Test
        SigV4ATest
        AllAuthenticationTest
        NoAuthenticationTest
        SigV4AuthenticationWithBodyTest
        SigV4AAuthenticationWithBodyTest
    ]
}

@auth([httpBearerAuth])
@http(method: "POST", uri: "/only-bearer", code: 201)
operation OnlyBearerTest {
    input: OnlyBearerTestInput
    output: OnlyBearerTestOutput
}

@input
structure OnlyBearerTestInput {}

@output
structure OnlyBearerTestOutput {}

@auth([sigv4])
@http(method: "POST", uri: "/only-sigv4", code: 201)
operation OnlySigV4Test {
    input: OnlySigV4TestInput
    output: OnlySigV4TestOutput
}

@input
structure OnlySigV4TestInput {}

@output
structure OnlySigV4TestOutput {}

@auth([sigv4a, sigv4])
@http(method: "POST", uri: "/sigv4a", code: 201)
operation SigV4ATest {
    input: SigV4ATestInput
    output: SigV4ATestOutput
}

@input
structure SigV4ATestInput {}

@output
structure SigV4ATestOutput {}

@http(method: "POST", uri: "/all-authentication", code: 201)
operation AllAuthenticationTest {
    input: AllAuthenticationTestInput
    output: AllAuthenticationTestOutput
}

@input
structure AllAuthenticationTestInput {}

@output
structure AllAuthenticationTestOutput {}

@auth([])
@http(method: "POST", uri: "/no-authentication", code: 201)
operation NoAuthenticationTest {
    input: NoAuthenticationTestInput
    output: NoAuthenticationTestOutput
}

@input
structure NoAuthenticationTestInput {}

@output
structure NoAuthenticationTestOutput {}

@auth([sigv4])
@http(method: "POST", uri: "/sigv4-authentication-body", code: 201)
operation SigV4AuthenticationWithBodyTest {
    input: SigV4AuthenticationWithBodyTestInput
    output: SigV4AuthenticationWithBodyTestOutput
}

@input
structure SigV4AuthenticationWithBodyTestInput {
    input1: String
}

@output
structure SigV4AuthenticationWithBodyTestOutput {}


@auth([sigv4a, sigv4])
@http(method: "POST", uri: "/sigv4a-authentication-body", code: 201)
operation SigV4AAuthenticationWithBodyTest {
    input: SigV4AAuthenticationWithBodyTestInput
    output: SigV4AAuthenticationWithBodyTestOutput
}

@input
structure SigV4AAuthenticationWithBodyTestInput {
    input1: String
}

@output
structure SigV4AAuthenticationWithBodyTestOutput {}

