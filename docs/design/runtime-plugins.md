# Runtime Plugins Design

* **Type**: Design
* **Author(s)**: Aaron Todd, Reference Architecture

# Abstract

This document covers the design and implementation of the API for encapsulating shared
service client configuration logic via plugins. Plugins allow configuration logic to be shared between 
multiple service clients to set defaults or ensure that clients are configured uniformly. Plugins can be
registered with a service client and manipulate the resulting configuration before the client is 
instantiated. 

# Design

## Client Interface

The interface for a plugin is given below. It has a single method that is passed the configuration builder
for the client being built. 

```kotlin
/**
 * A type that modifies an SDK client's configuration object
 */
public interface SdkClientPlugin {
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

class FooPlugin : SdkClientPlugin {
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
class DefaultRegionPlugin(private val defaultRegion: String): SdkClientPlugin {
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

Plugins are registered by the user (via SPI, see below) or by the service via codegen. 

* **Client Plugins**: Plugins registered by the user.
* **Default Plugins**: Plugins registered via codegen/customizations.

Plugins are registered in the following order:
1. **Default Plugins**: in the order they are specified in codegen.
2. **Client Plugins**: in the order they are added by the user.


Client plugins are automatically discovered and registered via 
[Java Service Provider Interface (SPI)](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html). 
The [AbstractSdkClientBuilder](https://github.com/awslabs/smithy-kotlin/blob/36e612cb3c75b1b15c93ef38784af157e31db33b/runtime/runtime-core/common/src/aws/smithy/kotlin/runtime/client/AbstractSdkClientBuilder.kt#L14)
that every service client builder inherits is updated to include a `protected` mutable list of `plugins`. Prior
to instantiating the client, plugins are discovered and loaded from the classpath. Any plugin discovered is executed
just before the client is built such that any explicit configuration is visible to each plugin.


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
     * Add one or more instances of [SdkClientPlugin] that will be executed when the
     * [build] method is invoked. Each plugin is passed the current [config] builder
     * instance and has an opportunity to modify the final configuration before a new
     * client is instantiated.
     */
    protected val plugins: MutableList<SdkClientPlugin> = mutableListOf()

    /**
     * The global plugin classpath that all 
     */
    protected open val globalPluginPath: String
        get() = "..."
    
    protected abstract val serviceClientPluginPath: String

    /**
     * Auto load plugins from the classpath
     */
    private fun discoverPlugins(path: String): List<SdkClientPlugins> { ... }
    
    final override fun build(): TClient {
        if (enablePluginDiscovery()) {
            plugins.addAll(discoverPlugins(globalPluginPath))
            plugins.addAll(discoverPlugins(serviceClientPluginPath))
        }
        plugins.forEach { it.configureClient(config) }
        return newClient(config.build())
    }

    /**
     * Return a new [TClient] instance with the given [config]
     */
    protected abstract fun newClient(config: TConfig): TClient
}
```

**NOTE**: The abstract class definition above is only an example to demonstrate the design/concept.

Key points:

* Plugin auto discovery will only work on the JVM. Other platforms will have to load and apply plugins manually during construction. 
* The list of `plugins` is `protected` to allow codegen to inject default plugins/customizations for specific service clients.
* Plugins are discovered using both a global default path and a service client specific path. See [plugin discovery](#plugin-discovery).
* Plugin discovery can be disabled via a combination of environment and system properties (not shown).
    * Default plugins (via codegen) are always executed.

#### Plugin Discovery

Plugins are discovered from two paths:

1. **Global Plugin Path**: Plugins that apply to all instantiated service clients.
2. **Service Specific Path**: Plugins that apply to a specific service client.

Service client builders will generate a unique path for `serviceClientPluginPath` using their package name 
(e.g. `aws/sdk/kotlin/services/s3/config/plugins`). 


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

Plugins are only loaded once during initial client construction. They have no effect on cloned service clients.

### Resource Ownership Issues

Plugins have the opportunity to instantiate resources that require closing, e.g. configuring an `HttpClientEngine`. 
This presents a potential risk that resources are leaked from plugins when a plugin instantiates a resource.

The two plugins defined below will be used to discuss resource ownership issues:

```kotlin
// first plugin loaded
class Plugin1: SdkClientPlugin {
  override fun configureClient(config: SdkClientConfig.Builder<*>) {
    when (config) {
      is HttpClientConfig.Builder -> {
        config.httpClientEngine = CustomHttpClientEngine1()
      }
    }
  }
}

// some other plugin loaded
class Plugin2 : SdkClientPlugin {
  override fun configureClient(config: SdkClientConfig.Builder<*>) {
    when (config) {
      is HttpClientConfig.Builder -> {
        config.httpClientEngine = CustomHttpClientEngine2()
      }
    }
  }
}
```

#### Single Plugin

Assume that only `Plugin1` is loaded. In this scenario the resulting client configuration would use 
`CustomHttpClientEngine1`. 

Users are responsible for the lifetime of all resources handed to a service client (client's do not close any resource 
that they did not instantiate themselves). The engine instantiated by `Plugin1` would be leaked.

#### Multiple Plugins

Assume that both plugins are registered, with `Plugin1` executing before `Plugin2`. In this scenario
the resulting client configuration would use `CustomHttpClientEngine2` as it's HTTP engine. Both
`CustomHttpClientEngine1` and `CustomHttpClientEngine2` would be leaked. The former because it is overridden and 
"forgotten", the latter because it isn't managed by the SDK and will not be closed when the client is closed.


# Appendices

## Appendix: FAQ

**Why plugins off the client builder and not the configuration builder**?

Plugins apply _to_ service client configuration, they are not part _of_ it's configuration. Defining plugin registration
off of config would allow for plugins the potential to mutate the list of plugins itself (if exposed).
This would either need to be an error or detected and ran in a loop until no new plugins are detected. This would be
confusing and more difficult to get right.

**Why require type checking to get at more specific configuration**?

There is no way to provide a more concrete type that would allow plugins to be shared across clients or loaded automatically via SPI.
This mirrors how [interceptors](interceptors.md) work. To get at specific input and output types you have to type check them.

**This seems specific to JVM SPI, what about other platforms**?

Other KMP platforms (e.g. Native and JS) do not have an equivalent auto discovery mechanism. Plugins would need to be
applied manually anyway. Plugins are a convenience for code that can already be written manually and shared across
all platforms. Plugins can still be used on other platforms though by applying them during construction.

e.g.

```kotlin
val client = FooClient { // this: FooClient.Config.Builder
    FooPlugin().configureClient(this)
    BazPlugin().configureClient(this)
}
```

**What about per/operation default plugins or service customizations**?

The design for per/operation config is not really "per operation". It could more accurately be described as a way to
copy a service client with configuration overrides. This allows users to create "scoped" clients, whether that be
for a single operation or many. As such there is no way for operation specific plugins to be registered via codegen
through _this_ mechanism. Should the need arise to allow operation specific plugins `smithy-kotlin` codegen could
be updated to deal with operation specific configuration.

## Appendix: Alternatives

### Plugin Interface ALT 2 - Use the service client builder

An alternative interface for runtime plugin is to use the client builder instead of the client config builder:

```kotlin
public interface SdkClientPlugin {
    public fun configureClient(client: SdkClient.Builder<*, *, *>)
}
```

Plugins would work as before but have to type check the `config` property instead:

```kotlin
class DefaultRegionPlugin(private val defaultRegion: String): SdkClientPlugin {
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
be allowed to modify that it would be possible. 

## Additional References

* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)
* [Client Configuration](client-configuration.md)
* [Per Operation Config](per-op-config.md)

# Revision history

* 01/19/2022 - Created
