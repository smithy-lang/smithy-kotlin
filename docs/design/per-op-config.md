# per-op config
* **Type**: Design
* **Author(s)**: AWS SDK for Kotlin team

# abstract
The smithy-kotlin project generates clients with the ability to perform operation- or block-scoped config overrides.
This functionality is exposed by supporting a `withConfig` method on clients, which takes a config builder and returns a
new "subclient" which performs operations using the modified config. We also outline several proposed design
alternatives that were rejected.

# design
All code samples in this document assume the following basic client interface. The client has an arbitrary number of operations,
the declarations of which are omitted.
This particular client has two config
fields: a logging mode and an HTTP client to be used to make requests. Note that HTTP clients (`HttpClientEngine`) in
the smithy-kotlin runtime implement `Closeable`.

```kotlin
interface ServiceClient : SdkClient {
    companion object {
        fun invoke(block: Config.Builder.() -> Unit): ServiceClient { /* returns instance of implementation class DefaultServiceClient */ }
    }
    
    class Config private constructor(builder: Builder) {
        val logMode: SdkLogMode = builder.logMode ?: SdkLogMode.Default
        val httpEngine: HttpClientEngine = builder.httpEngine
        
        class Builder {
            var logMode: SdkLogMode? = null
            val httpEngine: HttpClientEngine? = null
            fun build(): Config = Config(this)
        }
    }
    
    // suspend fun operationA, operationB, etc...
}

class DefaultServiceClient(config: ServiceClient.Config) : ServiceClient { /* ... */ }
```

Client interfaces receive a new `withConfig` extension, which takes a config builder and returns a "child" client with
any fields specified in the builder applied to its config.

```kotlin
fun ServiceClient.withConfig(block: ServiceClient.Config.Builder.() -> Unit): ServiceClient { /* ... */ }
```

In practice, to perform one or more operations with a modified config in-place, it is most effective to make your call
within a `use()` block on the returned client to have the `close()` handled for you. It's still possible to pass the
subclient to an external background routine, and it may even outlive the parent client (see below).

The `withConfig` method is _more efficient_ than creating multiple clients if you are using runtime-managed `Closeable`
resources such as the default HTTP engine. Users of these defaults are tracked across clients as they are created, and
the underlying `Closeable` is only fully closed when the last remaining client using it is closed.

For user-owned `Closeable`s, nothing changes - clients do not handle closing these for you today, and any child clients
created by `withConfig` won't either.

Sample usage:
```kotlin
suspend fun main() {
    val client = ServiceClient { }
    
    client.operationA { /* ... */ }
    
    client.withConfig {
        logMode = SdkLogMode.LogRequestWithBody
    }.use { // for one-off or explicitly-scoped operation(s), a use {} block is most convenient
        it.operationA { /* ... */ }
    }
    
    launch {
        doBackgroundRoutine(client.withConfig {
            logMode = SdkLogMode.LogResponse
        })
        
        doBackgroundRoutine2(client.withConfig {
            httpEngine = YourHttpClientEngineImpl()
        })
    }
    
    // application continues...
}
```

Some observations about the above sample:
* Since no explicit HTTP engine is passed to construction of the root client, the SDK creates its own "managed" default.
* The first use of `withConfig` and `use` demonstrates how to make basic one-off calls. The defaulted HTTP engine is
  inherited by the child. When the service client is `closed()` as part of the `use { }` call, the runtime recognizes
  that others are still holding onto its HTTP engine and so it isn't fully closed.
* The client passed to `doBackgroundRoutine` inherits the default HTTP engine as well. Both the parent and subclient can
  be closed at any time in either order - the managed HTTP engine will only be reaped when both clients using it are
  closed.
* The user passes their own HTTP engine implementation in the `doBackgroundRoutine2` call, and thus is responsible for
  closing it separately from the client.

# rejected alternatives

## alt1: request object builder
Add an sdk-specific field on request builders (proposed name `withConfig` used in this sample). Client implementations
retrieve that builder and apply it to the config on specific requests.

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
                logMode = SdkLogMode.LogRequestWithBody
            }
        }
    }
}
```

Rejected because:
* Adding our own named field `withConfig` to a structure generated based on a model we don't own introduces a collision
  risk. There's nothing preventing a model author from adding their own "withConfig" field to a shape.
* Affords less flexibility. A caller may with to structure a series of operations as a function which accepts an
  arbitrarily configured client - this route removes the possibility for that separation, forcing the caller to hardcode
  config overrides in specific method calls.
* Prevents us from supporting "shape-only" (generate only client interface and input/output shapes, no implementation)
  codegen in the future, since the `withConfig` property requires additional mechanisms to be generated into the structs
  in order to be retrieved by the implemented operation.

## alt2: `withConfig(configBuilder, useBlock)`
Expose `withConfig` like in the accepted design, except it takes a direct `use` block. The subclient is closed after the
block is invoked and is therefore only valid within that block.

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
        logMode = SdkLogMode.LogRequestWithBody + SdkLogMode.LogResponseWithBody
    }) {
        client.operationA { /* ... */ }
    }
}
```

The final accepted design ultimately evolved from this. We argue that there's no reason to restrict the user to making
calls within this forced scope - we can allow the caller to save and pass around the returned client as long as we're
responsible with SDK-managed closeable resources that are shared throughout.

Additionally, the pattern enforced by this API (client only lives within `useBlock`) can still be expressed in the
accepted design simply by making the `use { }` call on the client yourself, as demonstrated in its example usage.

# revision history

* 12/14/2022 - Created
