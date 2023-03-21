$version: "2"
namespace com.test

use aws.auth#unsignedPayload

@httpBearerAuth
@httpApiKeyAuth(name: "X-Api-Key", in: "header")
@httpBasicAuth
@auth([httpApiKeyAuth])
service Test {
    version: "1.0.0",
    operations: [
        GetFooServiceDefault,
        GetFooOpOverride,
        GetFooAnonymous,
        GetFooOptionalAuth,
        GetFooUnsigned
    ]
}

operation GetFooServiceDefault {}

@auth([httpBasicAuth, httpBearerAuth])
operation GetFooOpOverride{}

@auth([])
operation GetFooAnonymous{}

@optionalAuth
operation GetFooOptionalAuth{}

@unsignedPayload
operation GetFooUnsigned{}
