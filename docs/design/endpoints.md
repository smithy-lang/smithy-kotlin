# Endpoints

* **Type**: Design
* **Author(s)**: Luc Talatinian

This document discusses the design approach to modern endpoint resolution (Endpoints v2.0) in the Kotlin SDK.

## Overview
The Smithy IDL exposes a "rules language", which allows a model author to define the programmatic steps that must be
taken in order to determine the endpoint that calls should be made to. This language is provided inline with the model
as the `smithy.rules#endpointRuleSet` service trait. The schema for this trait will be described as part of Smithy's
extended documentation in the future.

The smithy-kotlin repository implements the core code-generation for endpoint providers based on rule sets defined this
way, as well as the middleware that sources the parameters for the resolver and calls it on each request.

## Core Concepts
### Endpoint
The `Endpoint` is the result of calling an endpoint provider implementation at runtime to determine what endpoint to use
when making service calls. The type is public and shared across all service clients, and all provider implementations
MUST return values of this type. All values of the endpoint are provided by the ruleset.

The (annotated) type is provided here:

```kotlin
public data class Endpoint @InternalApi constructor(
    /**
     * The actual URL against which service operations are called.
     */
    public val uri: Url,

    /**
     * Any additional HTTP headers to send on calls to this endpoint.
     */
    public val headers: Headers = Headers.Empty,

    /**
     * SDK-specific attributes tied to this endpoint.
     * 
     * The values within the set of attributes are unstable. Additionally, custom provider implementations cannot set
     * these values as part of endpoints they resolve to (hence the internal annotation). They can only be specified
     * in the Smithy ruleset and returned as part of generated code therein.
     * 
     * SDK authors are encouraged to define strongly-typed extensions for values relevant to their SDK's operation.
     */
    @InternalApi
    public val attributes: Attributes = Attributes(),
)
```

### Endpoint provider

Providers house the core logic for determining the endpoint to use **on a per-request basis**. Providers are generated
as both a service-specific interface (to be tied to that service's specific provider parameters type and allow for
custom implementations) and a default provider that implements the logic defined in the model.

```kotlin
public fun interface EndpointProvider {
    public suspend fun resolveEndpoint(params: ServiceEndpointParams): Endpoint
}
```
Client config will include a field for the resolver unique to that service, and SDK operations will use the generated
default unless specified otherwise by the caller.

### Provider library
The endpoint rulesets as provided by Smithy require some "standard-library"-type functions that support or drive the
evaluation of the rules.

Some of these are trivial (eg. "is a value non-null") and are generated inline as part of the
ruleset evaluation. The more complex functions (eg. URL parsing) are hosted in the runtime at
`package aws.smithy.kotlin.runtime.http.endpoints.functions`. Runtime-hosted standard library functions are annotated as
internal and should only be called by code-generated endpoint providers.

### Provider parameters
Each service client has a specific set of parameters passed to invocations of its endpoint provider. Endpoint resolution
is a pre-serialization operation, and the rules language specifies several ways in which parameters are sourced.

While provider parameters are defined per-service, the sources of the values may vary per-operation based on context
parameters defined in the model.

#### `clientContextParams`
Service-level annotation. Defines an additional configuration field to be added to the service's client config. Bound to
a field on the provider parameters at resolution time.

#### `staticContextParams`
Operation-level annotation. Defines a static value to be bound to a provider parameter at resolution time.

#### `contextParams`
Operation-level annotation. Marks a field on an operation to be bound to a provider parameter at resolution time.

#### builtins
The ruleset specification allows for the definition of builtins, or SDK-specific values that are bound to provider
parameters at the time of resolution. As a generic codegen provider, smithy-kotlin doesn't implement any builtins on its
own, but rather exposes an interface in the middleware generator for SDK authors to define and resolve their own
builtins.

For example, the AWS SDK has special builtins for the AWS region, as well as for concepts unique to the AWS ecosystem
such as the usage of dual-stack endpoints. The AWS-specific generator implementation over top of the base smithy-kotlin
one is responsible for providing the logic to code-generate the sourcing of these builtins in the resolver middleware
(detailed below).

## Codegen
### Resolver
Codegen for resolvers works much like any other extendable generated construct in smithy-kotlin - a base generator is
implemented to perform to walk the rules tree and generate the core logic. This base class can be extended on by SDK
authors as needed.

```kotlin
public open class EndpointProviderGenerator(
    public val writer: KotlinWriter,
    public val ruleset: EndpointRuleset,
    // ...
) {
    /**
     * Main codegen routine - walk the rules tree and generate the provider.
     */
    public fun render()

    /**
     * Resolve a standard library function referenced in a ruleset.
     */
    public open fun resolveFunction(func: String)
}
```

### Middleware
Endpoint resolution, whether generated or written by the caller, is performed per-request as a pre-operation middleware.
At runtime, endpoint resolution is comprised of two principal tasks:
1. construct a set of provider parameters, sourcing the values as specified by the model (context params)
2. using the sourced parameters, call the provider to resolve to an endpoint, pointing the request to it

Since endpoint resolution is performed on a per-operation basis, and the sourcing of provider parameters can vary
per-operation, middleware must now be generated per-operation (this differs from the original implementation, where the
endpoint resolver middleware was static across operations and its implementation was part of the runtime).

```kotlin
public open class EndpointResolverMiddlewareGenerator(
    public val writer: KotlinWriter,
    public val operation: OperationShape,
    public val ruleset: EndpointRuleset,
    // will likely need some additional params such as service shape etc.
) {
    /**
     * Generates code to construct a set of provider parameters based on the specific service, call the provider
     * with those parameters, and modify the request (and handle failure to resolve if that occurs).
     * 
     * Client/static/general context parameters are inspected here to determine where to source values.
     */
    public fun render()
    
    /**
     * SDK authors extend this method to handle builtins.
     */
    public open fun resolveBuiltin(value: String)

    /**
     * Hook for implementers to generate additional custom code that acts on the resolved endpoint, most likely to
     * delegate further action based on the contents of said endpoint's attributes field.
     * The AWS SDK, for example, will use this to inspect and handle signing scope information.
     */
    public open fun renderPostResolution()
}
```

This will generate middleware with the following signature:
```kotlin
public class OperationMiddleware(
    private val config: SmithyService.Config,
    private val request: OperationRequest,
    private val provider: EndpointProvider,
) : ModifyRequestMiddleware {
    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        /**
         * Source provider parameters, resolve endpoint, set it on the request.
         */
    }
}
```

## Example

The following is an example ruleset attached to an arbitrary service definition with the ID "SmithyService" (the
definition itself is omitted).

The ruleset describes a simple if-else case: if the sole required parameter `ResourceId` contains a specific prefix,
route this request against a special-case endpoint. Otherwise, fall back to a constant default. The `substring` function
used in the ruleset is an example of a standard-library function used by endpoint resolvers.

This particular ruleset is kept simple for the purpose of example, naturally, a ruleset can be of arbitrary complexity as
required by the service.

```json
{
  "parameters": {
    "ResourceId": {
      "type": "string",
      "required": "true"
    }
  },
  "rules": [
    {
      "documentation": "use a special endpoint for government resources",
      "type": "endpoint",
      "conditions": [
        {
          "fn": "substring",
          "argv": [
            {"ref": "ResourceId"},
            0,
            5,
            false
          ],
          "assign": "resourceIdPrefix"
        },
        {
          "fn": "stringEquals",
          "argv": [
            {"ref": "resourceIdPrefix"},
            "gov."
          ]
        }
      ],
      "endpoint": {
        "url": "https://gov.api"
      }
    },
    {
      "documentation": "fallback to global endpoint",
      "conditions": [],
      "endpoint": {
        "url": "https://global.api"
      }
    }
  ]
}
```
*Note*: The signature of the substring method is `substring(s: String, start: Int, end: Int, reverseIndices: false): String`.
The argv property in the corresponding function call describes how to source the arguments (or input constants directly
in the case of the indices).

Given a sample operation `GetResource`, with a single operation input `resource` bound to the `ResourceId` provider
parameter via context traits, this ruleset would roughly generate the following code:

```kotlin
public data class SmithyServiceEndpointParams(
    public val resourceId: String?,
) {
    init {
        require(resourceId != null) { "param resourceId is required" }
    }
}

public fun interface SmithyServiceEndpointProvider {
    open suspend fun resolveEndpoint(params: SmithyServiceEndpointParams): Endpoint
}

public class DefaultSmithyServiceEndpointProvider: SmithyServiceEndpointProvider {
    public suspend fun resolveEndpoint(params: SmithyServiceEndpointParams): Endpoint {
        val resourceIdPrefix = substring(params.resourceId, 0, 5, false)
        if (resourceIdPrefix == "gov.") {
            // use a special endpoint for government resources
            return Endpoint(
                Url.parse("https://gov.api"),
            )
        }

        // fallback to global endpoint        
        return Endpoint(
            Url.parse("https://global.api"),
        )
    }
}

public class GetResourceResolveEndpointMiddleware(
    private val config: SmithyService.Config,
    private val request: GetResourceRequest,
    private val provider: EndpointProvider,
) : ModifyRequestMiddleware {
    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        val params = SmithyServiceEndpointParams {
            resourceId = request.resource
        }
        val endpoint = provider.resolveEndpoint(params)
            
        setRequestEndpoint(req, endpoint)
        
        // further action can be taken here as part of renderPostResolution...
        
        return req
    }
}
```
