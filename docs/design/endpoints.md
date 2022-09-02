# Endpoints

* **Type**: Design
* **Author(s)**: Luc Talatinian

This document discusses the design approach to modern endpoint resolution (Endpoints v2.0) in the Kotlin SDK.

## Overview
The Smithy IDL exposes a "rules language", which allows a model author to define the programmatic steps that must be
taken in order to determine the endpoint that calls should be made to. This language is provided inline with the model
as the `smithy.rules#endpointRuleSet` service trait. The schema for this trait will be described as part of Smithy's
extended documentation in the future.

The smithy-kotlin project implements the core code generation for endpoint providers based on rule sets defined this
way, as well as the middleware that binds the parameters for the resolver and calls it on each request.

## Core Concept

### Endpoint
The `Endpoint` is the result of calling an endpoint provider implementation at runtime to determine what endpoint to use
when making service calls. The type is public and shared across all service clients, and all provider implementations
MUST return values of this type. All values of the endpoint are provided by the rule set.

The annotated type is provided here:

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
     * in the Smithy rule set and returned as part of generated code therein.
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
The endpoint rule sets as provided by Smithy require some "standard-library"-type functions that support or drive the
evaluation of the rules.

Some of these are trivial (e.g. "is a value non-null") and are generated inline as part of the
rule set evaluation. The more complex functions (e.g. URL parsing) are hosted in the runtime at
`package aws.smithy.kotlin.runtime.http.endpoints.functions`. Runtime-hosted standard library functions are annotated as
internal and should only be called by code-generated endpoint providers.

### Provider parameters

Each service client has a specific set of parameters passed to invocations of its endpoint provider. The rules language
specifies what these parameters are, and annotations throughout the model define how they are bound.

Parameters are either a string or a boolean, and can be specified as required. For example, a service might define the
following parameters:
```json
{
  "itemId": {
    "type": "string",
    "required": true
  },
  "usePreview": {
    "type": "boolean"
  },
  "accountId": {
    "type": "string",
    "builtin": "CustomSDK::AccountId"
  }
}
```

For every operation, an SDK implementation for this service must construct a set of provider parameters, bind
values to them as specified in the model, and call the provider to resolve to an endpoint using those values.

The following annotations determine how parameter values are bound at call time:

#### `clientContextParams`

Service-level annotation. Defines an additional configuration field to be added to the service's client config. Bound to
a field on the provider parameters at resolution time.

For example, given the following parameters/model snippet:

```json
{
  "optInBeta": {
    "type": "boolean"
  }
}
```

```smithy
@clientContextParams(
    optInBeta: {type: "boolean", documentation: "opt in to the beta environment for this service"}
)
service BasicService {
    // ...
}
```

The client config for this service will be generated with an `optInBeta` field, the value of which will be bound for the
corresponding endpoint parameter field at resolution.

#### `staticContextParams`

Operation-level annotation. Defines a static value to be bound to a provider parameter at resolution time.

If a static context param targets a value previously declared as a client context param, the static value
overrides the one configured by the client for that call.

For example, given the following parameters/model snippet:

```json
{
  "optInBeta": {
    "type": "boolean"
  },
  "regionPrefix": {
    "type": "string"
  }
}
```

```smithy
@clientContextParams(
    optInBeta: {type: "boolean", documentation: "opt in to the beta environment for this service"}
    regionPrefix: {type: "string", documentation: "the country code for the client region"}
)
service BasicService {
    operations: [
        GetItem,
        GetSecretItem
    ]
}

@staticContextParams(
    optInBeta: {value: false}
)
operation GetItem {
    input: GetItemInput;
}

structure GetItemInput { }

operation GetSecretItem {
    input: GetSecretItemInput;
}

structure GetSecretItemInput { }
```

* the value of `optInBeta` would always be `false` for `GetItem`, regardless of client config, whereas `GetSecretItem`
  would bind from the client config
* the value of `regionPrefix` would bind from the client config for both calls

#### `contextParams`

Annotates a field in a structure which has been designated as an operation's input. Designates a field on that operation
to be bound to a provider parameter at resolution time.

If a context param targets a value previously declared as a client context param, the per-operation value overrides the
one configured by the client for that call.

For example, given the following parameters/model snippet:

```json
{
  "regionPrefix": {
    "type": "string"
  }
}
```

```smithy
@clientContextParams(
    regionPrefix: {type: "string", documentation: "the country code for the client region"}
)
service BasicService {
    operations: [
        GetItem,
        GetSecretItem
    ]
}

operation GetItem {
    input: GetItemInput;
}

structure GetItemInput {
    @contextParam(name: "regionPrefix")
    regionPrefix: string;
}

operation GetSecretItem {
    input: GetSecretItemInput;
}

structure GetSecretItemInput { }
```

The value of `regionPrefix` would use the request value for `GetItem`, whereas `GetSecretItem` would bind from the
client config.

#### Built-ins

The rule set specification allows for the definition of built-ins, which are SDK-specific values that are bound to
provider parameters at the time of resolution. As a generic codegen provider, smithy-kotlin doesn't implement any
built-ins on its own but rather exposes an interface in the middleware generator for SDK authors to define and resolve
their own built-ins.

For example, the AWS SDK has a special built-in for the AWS region. Given the following parameter declaration:

```json
{
  "region": {
    "type": "string",
    "builtin": "AWS::Region"
  }
}
```

The base middleware generator cannot render the binding of this value. The AWS SDK must implement custom logic to bind
this value from the client config.

## Codegen
### Package "endpoints"
The provider parameters and interface will generate to a new top-level `endpoints` package.

### Provider parameters

Provider parameters will be generated as a data class, since backwards compatability of parameter values is guaranteed (
field order MUST be preserved, and obsolete values are marked deprecated and not removed).

The presence of required values is checked at construction, with an error thrown if any are missing.

The generation includes DSL builder support for several reasons:
1. caller convenience - SDK users are able to implement and call their own endpoint providers
2. doing so provides greater flexibility for codegen for middleware - the binding of a value can be expressed as an
   arbitrary set of statements, rather than being restricted to a single expression in a constructor argument list
```kotlin
public data class ServiceEndpointParams(
    // ...args
) {
    init {
        // check required args
    }
    
    public companion object {
        public operator fun invoke(block: Builder.() -> Unit): ServiceEndpointParams
    }
    
    public class Builder internal constructor() {
        // ...
    }
}
```
### Resolver
Codegen for resolvers works much like any other extendable generated construct in smithy-kotlin: a base generator is
implemented to walk the rules tree and generate the core logic. This base class can be extended by SDK
authors as needed.

```kotlin
public open class EndpointProviderGenerator(
    public val writer: KotlinWriter,
    public val ruleSet: EndpointRuleset,
    // ...
) {
    /**
     * Main codegen routine - walk the rules tree and generate the provider.
     */
    public fun render()

    /**
     * Render a standard library function referenced in a rule set.
     */
    public open fun renderFunction(func: String, args: List<FunctionArg>)
}
```

### Middleware

Endpoint resolution, whether generated or written by the caller, is performed per-request as a pre-serialization
middleware. At runtime, endpoint resolution is comprised of two principal tasks:
1. construct a set of provider parameters, binding the values as specified by the model (context params)
2. using the bound parameters, call the provider to resolve to an endpoint, pointing the request to it

Operations that don't influence provider parameter values (i.e. operations with no associated `staticContextParams`
or `clientContextParams`) can use a default middleware generated once for the service. Other operations will need to
have unique middlewares generated to bind those specific values.

```kotlin
public open class EndpointResolverMiddlewareGenerator(
    public val writer: KotlinWriter,
    public val operation: OperationShape,
    public val ruleSet: EndpointRuleset,
    // will likely need some additional params such as service shape etc.
) {
    /**
     * Generates code to construct a set of provider parameters based on the specific service, call the provider
     * with those parameters, and modify the request (and handle failure to resolve if that occurs).
     * 
     * Client/static/general context parameters are inspected here to determine how to bind values.
     */
    public fun render()

    /**
     * Implement this API to generate additional parameters to be passed to the middleware.
     * By default, the middleware takes two parameters:
     * 1. service client config
     * 2. operation's request object
     * 
     * The implementer is responsible for code generation of the middleware installation.
     */
    public open fun getMiddlewareParams(): List<Symbol>
    
    /**
     * SDK authors extend this method to handle built-ins.
     * Called within the builder lambda for the parameters object.
     */
    public open fun renderBuiltin(name: String)

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
    private val config: SmithyService.Config, // has the endpoint provider
    private val request: OperationRequest,
) : ModifyRequestMiddleware {
    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        /**
         * Source provider parameters, resolve endpoint, set it on the request.
         */
    }
}
```

## Example

The following is an example rule set attached to an arbitrary service definition with the ID "SmithyService" (the
definition itself is omitted).

The rule set describes a simple if-else case: if the sole required parameter `ResourceId` contains a specific prefix,
route this request against a special-case endpoint. Otherwise, fall back to a constant default. The `substring` function
used in the rule set is an example of a standard-library function used by endpoint resolvers.

This particular rule set is kept simple for the purpose of example. A rule set can be of arbitrary complexity as
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
            4,
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
      "type": "endpoint",
      "conditions": [],
      "endpoint": {
        "url": "https://global.api"
      }
    }
  ]
}
```
*Note*: The signature of the substring method is `substring(s: String, start: Int, end: Int, reverseIndices: false): String`.
The argv property in the corresponding function call provides the arguments, each of which will either be a variable
reference or constant
value.

Given a sample operation `GetResource` with a single operation input `resource` bound to the `ResourceId` provider
parameter via a `contextParam` trait, this rule set would generate roughly the following code:

```kotlin
// package *.services.smithyservice.endpoints

public data class SmithyServiceEndpointParams(
    public val resourceId: String?,
) {
    init {
        require(resourceId != null) { "param resourceId is required" }
    }

    // builder implementation ...
}

// package *.services.smithyservice.endpoints

public fun interface SmithyServiceEndpointProvider {
    suspend fun resolveEndpoint(params: SmithyServiceEndpointParams): Endpoint
}

// package *.services.smithyservice.internal

internal class DefaultSmithyServiceEndpointProvider: SmithyServiceEndpointProvider {
    public suspend fun resolveEndpoint(params: SmithyServiceEndpointParams): Endpoint {
        val resourceIdPrefix = substring(params.resourceId, 0, 4, false)
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

// package *.services.smithyservice.internal

internal class GetResourceResolveEndpointMiddleware(
    private val config: SmithyService.Config,
    private val request: GetResourceRequest,
) : ModifyRequestMiddleware {
    override suspend fun modifyRequest(req: SdkHttpRequest): SdkHttpRequest {
        val params = SmithyServiceEndpointParams {
            resourceId = request.resource
        }
        val endpoint = config.provider.resolveEndpoint(params)
            
        setRequestEndpoint(req, endpoint)
        
        // further action can be taken here as part of renderPostResolution...
        
        return req
    }
}
```
