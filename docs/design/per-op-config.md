# Per-operation configuration

* **Type**: Design
* **Author(s)**: Luc Talatinian, Ian Smith Botsford, Aaron Todd, Matas Lauzadis

# Abstract

Service clients are configured at creation time and use the same configuration for every operation for the lifetime of
the client. It is desirable to enable overriding a subset of client configuration for one or more operations (e.g. using
different HTTP settings, logging mode, etc.). This document covers the design and implementation of
the API for overriding service client configuration on a per-operation basis, and outlines several rejected design
alternatives.

# Design

All code samples in this document assume the following basic client interface. The client has an arbitrary number of
operations, the declarations of which are omitted.

This particular client has two config fields: a logging mode and an HTTP client to be used to make requests. Note that
HTTP clients (`HttpClientEngine`) in the smithy-kotlin runtime implement `Closeable`.

```kotlin
interface ServiceClient : SdkClient {
    companion object {
        fun invoke(block: Config.Builder.() -> Unit): ServiceClient { /* returns instance of implementation class DefaultServiceClient */ }
    }
    
    class Config private constructor(builder: Builder) {
        val logMode: LogMode = builder.logMode ?: LogMode.Default
        val httpEngine: HttpClientEngine = builder.httpEngine
        
        class Builder {
            var logMode: LogMode? = null
            val httpEngine: HttpClientEngine? = null
            fun build(): Config = Config(this)
        }
    }
    
    // suspend fun operationA, operationB, etc...
}

class DefaultServiceClient(config: ServiceClient.Config) : ServiceClient { /* ... */ }
```

Client interfaces have a `withConfig` extension, which takes a config builder and returns a new client with
any fields specified in the builder applied to its config.

```kotlin
fun ServiceClient.withConfig(block: ServiceClient.Config.Builder.() -> Unit): ServiceClient { /* ... */ }
```

The client returned from `withConfig` exists independently of the original. It has its own lifetime and should be closed
when no longer needed. Any runtime-managed closeable resources will be kept alive until all clients using them have been
closed.

The `withConfig` method is _more efficient_ than creating multiple clients from scratch (either directly from explicit
`Config` or via `fromEnvironment`) when using runtime-managed `Closeable` resources such as the default HTTP engine.
Instances which use these defaults are tracked across clients as they are created, and the underlying `Closeable` is
only fully closed when the last remaining client using it is closed.

Sample usage:
```kotlin
suspend fun main() {
    val client = ServiceClient { }
    
    client.operationA { /* ... */ }
    
    client.withConfig {
        logMode = LogMode.LogRequestWithBody
    }.use { // for one-off or explicitly-scoped operation(s), a use {} block is most convenient
        it.operationA { /* ... */ }
    }
    
    launch {
        doBackgroundRoutine(client.withConfig {
            logMode = LogMode.LogResponse
        })
        
        doBackgroundRoutine2(client.withConfig {
            httpEngine = YourHttpClientEngineImpl()
        })
    }
    
    // application continues...
}
```

Some observations about the above sample:
* Since no explicit HTTP engine is passed to construction of the initial client, the SDK creates its own "managed" default.
* The first use of `withConfig` and `use` demonstrates how to make basic one-off calls. The default HTTP engine is
  shared with the new client. When the service client is closed as part of the `use { }` call, the runtime recognizes
  that others are still holding onto its HTTP engine and so it isn't fully closed.
* The client passed to `doBackgroundRoutine` shares the default HTTP engine as well. Both clients be closed at any time
  in either order&mdash;the managed HTTP engine will only be closed when both clients using it are closed.
* The user passes their own HTTP engine implementation in the `doBackgroundRoutine2` call, and thus is responsible for
  closing it separately from the client.

# Rejected alternatives

## Alt1: optional config on operation calls

Operation methods have the ability to directly override config. There are a number of ways to implement this alternative:
```kotlin
interface ServiceClient : SdkClient {
    // alt1.1: defaulted param
    suspend fun operationA(input: OperationARequest, config: Config? = null): OperationAResponse

    // alt1.2: Trailing lambda builder
    suspend fun operationB(input: OperationBRequest, block: Config.Builder.() -> Unit = {}): OperationBResponse

    class Config {
        // alt1.1 is enhanced by a copy() method on client config
        fun copy(builder: Builder.() -> Unit): Config {
            // return new instance with overrides applied, sharing managed closeables like in the accepted design
        }
    }
}
```

Sample usage:
```kotlin
suspend fun main() {
    val client = ServiceClient { /* ... */ }

    val requestA = OperationARequest { /* ... */ }
    client.operationA(requestA)
    client.operationA(requestA, client.config.copy {
        logMode = LogMode.LogRequestWithBody
    })

    val requestB = OperationBRequest { /* ... */ }
    client.operationB(requestB)
    client.operationB {
        // existing builder extension - build request here
    }
    client.operationB(requestB) {
        logMode = LogMode.LogResponseWithBody
    }
}
```

Rejected because:

* Both introduce operation signature bloat.
* Neither mix well with the adopted builder-based overloads that the SDK supports. Mixed usage of the two may adversely
  impact an author's ability to produce readable and maintainable code.
* For alt1.1 specifically, the API design may misguide the caller into constructing a new config rather than performing
  a copy on the existing client config, and doing so could duplicate the creation of expensive resources (e.g. HTTP
  engine).

## Alt2: request object builder

Request builders have an SDK-specific config override call (proposed name `withConfig` used in this sample). Client
implementations retrieve that builder and apply it to the config on specific requests.
```kotlin
// showing definition of sample client operationA input
class OperationARequest private constructor(builder: Builder) {
    val requestValue: Int? = builder.requestValue
    // ...
    class Builder {
        var requestValue: Int? = null
        // ...
        fun withConfig(configBuilder: ServiceClient.Config.Builder.() -> Unit) { /* ... */ }
    }
}
```

Sample usage:
```kotlin
suspend fun main() {
    ServiceClient().use {
        it.operationA {
            requestValue = 1
        }
        it.operationA {
            requestValue = 2
            withConfig {
                logMode = LogMode.LogRequestWithBody
            }
        }
    }
}
```

Rejected because:

* Adding custom un-modeled fields to (Smithy) structures introduces the possibility of collision with future model
  updates.
* Affords less flexibility. A caller may wish to structure a series of operations as a function which accepts an
  arbitrarily configured client&mdash;this route removes the possibility for that separation, forcing the caller to hardcode
  config overrides in every call where it is required.
* Complicates supporting "shape-only" (generate only client interface and input/output shapes, no implementation)
  codegen in the future, since the `withConfig` property requires additional mechanisms to be generated into the structs
  in order to be retrieved by the implemented operation.

## Alt3: `withConfig(configBuilder, useBlock)`

Expose `withConfig` like in the accepted design, except it takes a direct `use` block. The inner client is closed after
the block is invoked and is therefore only valid within that block.

```kotlin
fun ServiceClient.withConfig(
    configBuilder: ServiceClient.Config.Builder.() -> Unit,
    useBlock: (ServiceClient) -> Unit,
) {
    // ...
}
```

Sample usage:
```kotlin
suspend fun main() {
    val client = ServiceClient { }
    client.operationA { /* ... */ }
  
    client.withConfig({
        logMode = LogMode.LogRequestWithBody + LogMode.LogResponseWithBody
    }) {
        client.operationA { /* ... */ }
    }
}
```

This alternative was rejected in favor of the proposed design since it accomplishes the same thing while expanding the
scope of use. The SDK can allow the caller to save and pass around the returned client as long as the runtime is
responsible with managed closeable resources that are shared throughout. The use of two lambda arguments was also deemed
non-idiomatic.

Additionally, the pattern enforced by this API (client only lives within `useBlock`) can still be expressed in the
accepted design simply by making the `use { }` call on the client itself, as demonstrated in its example usage.

# Revision history

* 12/14/2022 - Created
