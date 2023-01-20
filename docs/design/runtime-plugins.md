# Runtime Plugins Design

* **Type**: Design
* **Author(s)**: Aaron Todd, Reference Architecture

# Abstract

This document covers the design and implementation of the API for encapsulating shared
service client configuration logic via plugins. Plugins allow configuration logic to be shared between 
multiple service clients to set defaults or ensure that clients are configured uniformly. Plugins can be
registered with service client builders and manipulate the resulting configuration before the client is 
instantiated. 

# Design

## Client Interface

The interface for a plugin is given below. It has a single method that is passed the configuration builder
for the client being built. 

```kotlin
/**
 * A type that modifies an SDK client's configuration object
 */
public interface RuntimePlugin {
    /**
     * Modify the provided client configuration
     */
    public fun configureClient(config: SdkClientConfig.Builder<*>): Unit
}
```

The parameter `config` is constrained to only inherited interface all service client configuration builders share.
Configuration coming from other "mixin" builders can be accessed through type checking to more specific types. 
See the [example plugin](#example-plugin) below.

### Example Plugin

An example plugin is given below. It demonstrates how specific configuration can be accessed by type checking
the builder given. 

NOTE: This is not an exhaustive list of configuration interfaces that a specific config may inherit

```kotlin

class FooPlugin : RuntimePlugin {
    override fun configureClient(config: SdkClientConfig.Builder<*>) {
        when (config) {
            is AwsSdkClientConfig.Builder -> {
                // AWS specific configuration
                config.region = "us-west-2"
            }
            is TracingClientConfig.Builder -> {
                // Tracing configuration
                config.tracer = OpenTelemetryTracer()
            }
            is HttpClientConfig.Builder -> {
                // HTTP configuration
                config.httpClientEngine = MyHttpClientEngine()
                config.interceptors += MyInterceptor()
            }
            else -> {
                // always available from `SdkClientConfig.Builder`
                config.retryStrategy = CustomRetryStrategy()
                config.sdkLogMode = SdkLogMode.LogRequest
            }
        }
    }

}

```

While plugins can access all of these configuration fields they are more likely to be split into multiple
plugins with each having a specific purpose (e.g. setting a default region, tracing config, etc). This makes
plugins more re-usable and shareable.

A more representative plugin is given below that sets a default AWS region if one is not set already:

```kotlin
class DefaultRegionPlugin(private val defaultRegion: String) {
    override fun configureClient(config: SdkClientConfig.Builder<*>) {
        when (config) {
            is AwsSdkClientConfig.Builder -> {
                if (config.region == null) {
                    config.region = defaultRegion
                }
            }
        }
    }
}
```

### Plugin Registration

Plugins are registered by the user at runtime or by the service via codegen. 

* **Client Plugins**: Plugins registered by the user.
* **Default Plugins**: Plugins registered via codegen/customizations.

Plugins are registered in the following order:
1. **Default Plugins**: in the order they are specified in codegen.
2. **Client Plugins**: in the order they are added by the user.


Client plugins are configured using the service _client_ builder. The [AbstractSdkClientBuilder](https://github.com/awslabs/smithy-kotlin/blob/36e612cb3c75b1b15c93ef38784af157e31db33b/runtime/runtime-core/common/src/aws/smithy/kotlin/runtime/client/AbstractSdkClientBuilder.kt#L14) 
that every service client builder inherits is updated to include a mutable list of `plugins`. Any plugin registered
is executed just before the client is built such that any explicit configuration is visible to each plugin.

```kotlin
/**
 * Abstract base class all [SdkClient] builders should inherit from
 */
@InternalApi
public abstract class AbstractSdkClientBuilder<
        TConfig : SdkClientConfig,
        TConfigBuilder : SdkClientConfig.Builder<TConfig>,
        out TClient : SdkClient,
        > : SdkClient.Builder<TConfig, TConfigBuilder, TClient> {

    /**
     * Add one or more instances of [RuntimePlugin] that will be executed when the
     * [build] method is invoked. Each plugin is passed the current [config] builder
     * instance and has an opportunity to modify the final configuration before a new
     * client is instantiated.
     */
    public val plugins: MutableList<RuntimePlugin> = mutableListOf()
    final override fun build(): TClient {
        plugins.forEach { it.configureClient(config) }
        return newClient(config.build())
    }

    /**
     * Return a new [TClient] instance with the given [config]
     */
    protected abstract fun newClient(config: TConfig): TClient

    /**
     * Set client [config] with the given [block]
     */
    public inline fun config(block: TConfigBuilder.() -> Unit): Unit { config.apply(block) }
}
```

The [SdkClientFactory](https://github.com/awslabs/smithy-kotlin/blob/36e612cb3c75b1b15c93ef38784af157e31db33b/runtime/runtime-core/common/src/aws/smithy/kotlin/runtime/client/SdkClientFactory.kt#L30)
interface is updated to change the `operator invoke` function from using the `TConfigBuilder` to `TClientBuilder`.

This is a **breaking** change. Users that instantiate clients using the explicit operator invoke syntax such as below
will break:

```kotlin
val s3 = S3Client {
    region = "us-east-2"
    ...
}
```

The updated version, including registering a plugin:

```kotlin
val s3 = S3Client {
    plugins += FooPlugin()
    // makes use of the new `config` DSL function on the abstract builder
    config {
        region = "us-east-2"
        ...
    }
}
```

Users will need to update how they create their clients by wrapping all existing configuration in the `config{ }` block.

### JVM SPI

TODO

### Interaction with per/op config

[Per operation config](per-op-config.md) introduced an API that allows users to create a new client from an existing one
with one or more configuration properties overridden. The use case for these cloned clients is to apply configuration
to a particular scope (e.g. one or more operations).

```kotlin
val s3 = S3Client { region = "us-east-1" }


s3.withConfig {
    region = "us-west-2"
}.use { s3West ->
   // perform operations against a different region 
}
```

The `withConfig` extension method uses the client configuration builder which means it would not have access to register
plugins that should only apply to the cloned client. 

**QUESTION**: Do we want to modify how per op config works before we land it? OR define a different way in which plugins would be applied
per/operation?  e.g.

```kotlin
val s3Clone = s3.toBuilder().apply { plugins += ScopedPerOpPlugin() }.build()
```

# Appendices

## Appendix: FAQ

**Why a breaking change to client instantiation**?

TODO

**Why plugins off the client builder and not the configuration builder**?

Plugins don't make much sense off of the configuration (builder) since the configuration is the object being modified.
Defining plugin registration off of config would allow for plugins the potential to mutate the list of plugins itself.
This would either need to be an error or detected and ran in a loop until no new plugins are detected. This would be
confusing and more difficult to get right.


**Why require type checking to get at more specific configuration**?

There is no way to provide a more concrete type that would allow plugins to be shared across clients or loaded automatically via SPI.
This mirrors how [interceptors](interceptors.md) work. To get at specific input and output types you have to type check them.


## Appendix: Alternatives

### Plugin Interface ALT 2 - Use the service client builder

An alternative interface for runtime plugin is to use the client builder instead of the client config builder:

```kotlin
public interface RuntimePlugin {
    public fun configureClient(client: SdkClient.Builder<*, *, *>)
}
```

Plugins would work as before but have to type check the `config` property instead:

```kotlin
class DefaultRegionPlugin(private val defaultRegion: String) {
    override fun configureClient(client: SdkClient.Builder<*, *, *>) {
        when (val config = client.config) {
            is AwsSdkClientConfig.Builder -> {
                if (config.region == null) {
                    config.region = defaultRegion
                }
            }
        }
    }
}

```

The advantage of this approach would be that if there are properties off of `SdkClient.Builder` that a plugin should
be allowed to modify that it would be possible. It has the same disadvantage as registering using the 
[config builder](#plugin-registration-alt-2---service-client-config-builder) w.r.t plugin mutation via plugins though.

### Plugin Registration ALT 2 - Service Client Config Builder

An alternative to using the service client builder for plugin registration is to use the _configuration_ builder.

e.g.

```kotlin
val S3Client {
    region = "us-west-2"
    plugins += FooPlugin()
}
```

This has the advantage of not requiring a breaking change. However, modeling it this way is confusing since plugins
modify the configuration they are being registered with. Modeling a plugin off of the client builder will also allow
for better extensibility going forward should we have client level properties that make sense to be modifiable via
a plugin.

See also "Why plugins off the client builder and not the configuration builder" in the [FAQ](#appendix-faq).

## Implementation Details


## Additional References

* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)
* [Client Configuration](client-configuration.md)
* [Per Operation Config](per-op-config.md)

# Revision history

* 01/19/2022 - Created
