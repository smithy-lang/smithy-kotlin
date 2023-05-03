# Client Configuration

* **Type**: Convention
* **Author(s)**: Aaron Todd

# Abstract

This document describes the structure and conventions used for service client configuration. 

# Conventions

This section describes general conventions and guidelines to be used for service client configuration. See the section
below for a concrete example of these conventions in practice.

* All generated service client config builders implement `SdkClientConfig.Builder`
* Domain specific configuration (e.g. HTTP, tracing, etc) should be used as a "mixin" rather than form a hierarchy
    * e.g. `HttpClientConfig` should not inherit from `SdkClientConfig`
* Configuration interfaces should live in a subpackage `config` of whatever root package they belong to.
* Configuration interfaces should have a nested `Builder` interface (e.g. `FooConfig.Builder`)

## Example Domain Specific Configuration

This section walks through what a new configuration interface and builder should look like in the runtime.

```kotlin

/**
 * Description (1)
 */
public interface FooConfig {  // 2
    /**
     * Property use description // 3
     */
    public val fooConfigProp: String  // 4

    public interface Builder {  // 5

        /**
         * Property configuration description // 6
         */
        public var fooConfigProp: String?  // 7
    }
}
```

1. A detailed description of what the configuration is used for and what might be found in it.
2. Configuration interface should be `public` and generally be `Xyz` with the suffix `Config` (e.g. `XyzConfig`).
3. A detailed description of how the property is used, which _may_ differ from the description of how to configure it.
    * This may delegate to the builder property docs. e.g. `Controls the how the foo does blah. See [Builder.fooConfigProp] for more details.`.
4. Configuration fields must be read-only (`val`) and should be immutable. Their type may differ from the builder type, e.g. `List` vs `MutableList` or be non-null where the configuration field is nullable.
    * These differences are handled in codegen by setting default values or mapping the builder type as required.
5. The configuration builder interface should be public and nested inside the configuration interface it is meant to build.
6. A detailed description of how the property is configured and how it is used. 
7. Builder fields should generally be `var` and/or mutable. Their type may be different from the immutable configuration property.

## Example Service Client

This section walks through an example service client configuration class, describing each of its components at a high level.

```kotlin
public interface BazClient : SdkClient { // 1

    override val serviceName: String
        get() = "baz"

    public override val config: Config // 2

    public companion object : SdkClientFactory<Config, Config.Builder, BazClient, Builder>() { // 3
        @JvmStatic
        override fun builder(): Builder = Builder()
    }

    public class Builder internal constructor() : AbstractSdkClientBuilder<Config, Config.Builder, BazClient>() { // 4
        override val config: Config.Builder = Config.Builder()
        override fun newClient(config: Config): BazClient = DefaultBazClient(config)  // 5
    }

    public class Config private constructor(builder: Builder) : SdkClientConfig, HttpClientConfig {  // 6
        override val logMode: LogMode = builder.logMode  // 7
        override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine
        val bazSpecificConfig: String? = builder.bazSpecificConfig

        public companion object { // 8
            public inline operator fun invoke(block: Builder.() -> kotlin.Unit): Config = Builder().apply(block).build()
        }

        public class Builder : SdkClientConfig.Builder<Config>, HttpClientConfig.Builder { // 9
            /**
             * Description
             */
            override var httpClientEngine: HttpClientEngine? = null  // 10

            /**
             * Description
             */
            override var logMode: LogMode = LogMode.Default

            /**
             * Description
             */
            var bazSpecificConfig: String? = null

            override fun build(): Config = Config(this)
        }
    }
}
```

NOTE: The example client interface above has intentionally left off KDoc comments for many of the fields for brevity.
NOTE: See the Appendix for definitions of some of these inherited interfaces (e.g. `SdkClientFactory`).

1. All generated service clients inherit from `SdkClient`
2. The `config` property (from `SdkClient`) may be overridden with a more specific configuration than what the inherited interface specifies (`SdkClientConfig`)
3. A companion object is generated that inherits default behavior from the runtime. See [client creation patterns](#client-creation-patterns).
4. A builder for creating a service client is generated. It inherits behavior from `AbstractSdkClientBuilder`
5. The internal concrete (generated) implementation of `BazClient` is provided. 
6. The immutable service client configuration container type. 
    * This will inherit from `SdkClientConfig` and `N` "mixin" configurations. Mixins are pulled in based on the model/customizations.
7. Configuration properties are set from the builder, possibly providing defaults when not set or mapping to different types.
8. The configuration companion object provides a convenience DSL like experience for instantiation, e.g. `val config = BazClient.Config { ... }`
9. The configuration builder inherits from `SdkClientConfig.Builder` and `N` "mixin" configuration builders. 
10. Default values are set for the configuration builder properties

## Client Creation Patterns

This section describes in more detail how service clients are created and configured using the `BazClient` described
in the [example service client](#example-service-client) section. These behaviors are a combination of runtime types and
codegen.

```kotlin
// explicit using DSL syntax inherited from companion `SdkClientFactory`
val c1 = BazClient { // this: BazClient.Config.Builder
    logMode = LogMode.LogRequest
}

// use of a builder explicitly, this could be passed around for example
val c2 = BazClient.builder().apply { // this: BazClient.Builder
    config.logMode = LogMode.LogRequest
}.build()

// "vended" using common code
val c3 = ClientVendingMachine.getClient(BazClient)


private object ClientVendingMachine {
    fun <
        TConfig: SdkClientConfig,
        TConfigBuilder: SdkClientConfig.Builder<TConfig>,
        TClient: SdkClient,
        TClientBuilder: SdkClient.Builder<TConfig, TConfigBuilder, TClient>
    > getClient(factory: SdkClientFactory<TConfig, TConfigBuilder, TClient, TClientBuilder>): TClient {
        val builder = factory.builder()
        // setting various configuration
        when(val config = builder.config) {
            is BazClient.Config.Builder -> {
                // access to configuration that may be specific to BazClient
                config.bazSpecificConfig = "specific"
            }
            is HttpClientConfig.Builder -> {
                // access to http related configuration
                config.httpClientEngine
            }
            else -> {
                // always available from generic bounds:
                config.logMode
            }
        }
        return builder.build()
    }
}
```

1. `c1` is created using DSL like syntax inherited from [SdkClientFactory](#sdkclientfactory)
2. `c2` is created by instantiating a service client builder explicitly using `BazClient.builder()`. 
This comes from the companion object and is a static method (defined in [SdkClientFactory](#sdkclientfactory))
3. `c3` is an example of what's possible. It is created using common code that inspects the builder type for specific
configuration (builder) mixins. This type of pattern could be used to centralize configuring a service client without
knowing the concrete type.

# Appendices

## Appendix: FAQ

**Why builders**?

Builders are useful for dealing with evolution as well as the ability to insert additional logic into the creation of 
some entity at the time it is built. The API of most builders should be "DSL" like in nature (properties over functions).
When possible the SDK will offer a DSL like experience for creating instances of types.

**Why no hierarchy of config builder classes to inherit from**?

The config interface (e.g. `FooConfig`) is an immutable collection of properties to be
consumed by something else (usually a service client). The configuration builder interface (`FooConfig.Builder`)
describes how to configure the properties. The builder interfaces (e.g. `FooConfig.Builder`) generally have no 
concrete implementation because the SDK will codegen a specific implementation by describing the configuration 
properties (including the default values that should be used when not given). 

## Appendix: Runtime Types

This section provides concrete examples of runtime types referenced in this document. These types may differ or evolve
from when this document was written without loss of understanding of conventions and guidelines described herein. 

### SdkClient

```kotlin
/**
 * Common interface all generated service clients implement
 */
public interface SdkClient : Closeable {
    /**
     * The name of the service client
     */
    public val serviceName: String

    /**
     * The client's configuration
     */
    public val config: SdkClientConfig

    public interface Builder<
            TConfig : SdkClientConfig,
            TConfigBuilder : SdkClientConfig.Builder<TConfig>,
            out TClient : SdkClient,
            > : Buildable<TClient> {

        /**
         * The configuration builder for this client
         */
        public val config: TConfigBuilder
    }
}
```

### SdkClientConfig

```kotlin
/**
 * Common configuration options for any generated SDK client
 */
public interface SdkClientConfig {
    /**
     * Controls the events that will be logged by the SDK, see [Builder.logMode].
     */
    public val logMode: LogMode
        get() = LogMode.Default

    public interface Builder<TConfig : SdkClientConfig> : Buildable<TConfig> {
        /**
         * Configure events that will be logged. By default, clients will not output
         * raw requests or responses. Use this setting to opt in to additional debug logging.
         *
         * This can be used to configure logging of requests, responses, retries, etc of SDK clients.
         *
         * **NOTE**: Logging of raw requests or responses may leak sensitive information! It may also have
         * performance considerations when dumping the request/response body. This is primarily a tool for
         * debug purposes.
         */
        public var logMode: LogMode
    }
}
```

### SdkClientFactory

```kotlin
/**
 * Interface all generated [SdkClient] companion objects inherit from.
 */
public interface SdkClientFactory<
        TConfig : SdkClientConfig,
        TConfigBuilder : SdkClientConfig.Builder<TConfig>,
        TClient : SdkClient,
        out TClientBuilder : SdkClient.Builder<TConfig, TConfigBuilder, TClient>,
        > {
    /**
     * Return a [TClientBuilder] that can create a new [TClient] instance
     */
    public fun builder(): TClientBuilder

    /**
     * Configure a new [TClient] with [block].
     *
     * Example
     * ```
     * val client = FooClient { ... }
     * ```
     */
    public operator fun invoke(block: TConfigBuilder.() -> Unit): TClient = builder().apply {
        config.apply(block)
    }.build()
}
```


### AbstractSdkClientBuilder

Abstract base class that all service client builder implementations inherit from. This allows the runtime to add
additional logic over time before or after a client is instantiated (e.g. this could be used to load and apply plugins
using SPI on the JVM).

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

    final override fun build(): TClient {
        return newClient(config.build())
    }

    /**
     * Return a new [TClient] instance with the given [config]
     */
    protected abstract fun newClient(config: TConfig): TClient
}
```

## Appendix: Additional References

* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)

# Revision history

* 01/12/2023 - Created
