# Kotlin Smithy SDK

* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document presents the high-level design of how Smithy shapes and traits will map to code in Kotlin. It dictates the
fundamental outline of the generated code and discusses exceptions or edge cases as necessary. The HTTP, Event Stream,
XML, and MQTT Smithy specifications will all receive their own separate design as an addendum to this one. The reason
for this approach is (1) this document is getting large on its own, (2) once we agree on the majority of the core spec
we can start making progress on the code generator in parallel, and (3) since we are exploring the possibility of using
Kotlin Multiplatform for the client-runtime we feel the need to do some more exploration and a POC before committing to
a direction.

# Design

## Core spec

Reference the [Smithy Core Spec](https://awslabs.github.io/smithy/1.0/spec/core/).

### Identifiers and naming

Kotlin keywords can be found [here](https://kotlinlang.org/docs/reference/keyword-reference.html). Kotlin has both hard
keywords and soft keywords (context sensitive). Codegen will escape generated identifiers with names from the set of
hard keywords (e.g., "null" will be escaped as `` `null` ``).

### Simple types

| Smithy Type  | Kotlin Type
|--------------|----------------
| `blob`       | `ByteArray`
| `boolean`    | `Boolean`
| `string`     | `String`
| `byte`       | `Byte`
| `short`      | `Short`
| `integer`    | `Int`
| `long`       | `Long`
| `float`      | `Float`
| `double`     | `Double`
| `bigInteger` | *undecided (see [#213](https://github.com/awslabs/smithy-kotlin/issues/213))
| `bigDecimal` | *undecided (see [#213](https://github.com/awslabs/smithy-kotlin/issues/213))
| `timestamp`  | *custom type provided by client runtime
| `document`   | *custom type provided by client runtime

#### `document` type

See [Document type](document-type.md) for more detail.

### Aggregate types

| Smithy type | Kotlin type
|-------------|-------------
| `list`      | `List`
| `set`       | `Set`
| `map`       | `Map`
| `structure` | *class
| `union`     | *sealed class

#### Structure

A [structure](https://awslabs.github.io/smithy/spec/core.html#structure) type represents a fixed set of named
heterogeneous members. In Kotlin this can be represented as either a normal class or a data class.

Traits of generated classes in Kotlin:

1. We generate standard classes for types rather than Kotlin's data classes. See
   [Domain class types in Kotlin SDK](domain-class-types-in-kotlin-sdk.md) for the reasoning.
1. We generate request and response classes with nullable properties, regardless of any modeling notions of
   required-ness. [See here](nullable-properties-in-sdk-domain-types.md) for
   discussion.
1. For the construction of requests, DSL-style builders are provided instead of constructors. This approach provides a
   more robust form of creation as we have more flexibility in how values are evaluated during instantiation of a type.

Non-boxed member values will be defaulted according to the spec:

> The default value of a byte, short, integer, long, float, and double shape that is not boxed is zero

All other types (aggegates list, set, structure, String, etc.) will be nullable and defaulted to null.

```
@enum(YES:{}, NO: {})
string SimpleYesNo

structure Baz {
    quux: String,
}

structure MyStruct {
    foo: String,
    bar: PrimitiveInteger,
    baz: Baz,
    yesno: SimpleYesNo,
}
```

Given the above Smithy structure shapes above we would generate the following:

```kotlin
class Baz private constructor(builder: Builder) {
    val quux: String? = builder.quux

    override fun toString(): String = "Baz(quux=$quux)"

    companion object {
        inline operator fun invoke(block: Builder.() -> Unit): Baz = Builder().apply(block).build()
    }

    fun copy(block: Builder.() -> kotlin.Unit = {}): Baz = Builder(this).apply(block).build()
    
    class Builder {
        var quux: String? = null

        internal constructor()
        constructor(baz: Baz): this() {
            this.quux = baz.quux
        }

        internal fun build(): Baz = Baz(this)
    }
}

class MyStruct private constructor(builder: Builder) {
    val foo: String? = builder.foo
    val bar: Int = builder.bar
    val baz: Baz? = builder.baz
    val yesno: SimpleYesNo? = builder.yesno

    override fun toString(): String {
        return "MyStruct(foo=$foo, bar=$bar, baz=$baz, yesno=$yesno)"
    }

    fun copy(block: Builder.() -> Unit = {}): MyStruct = Builder(this).apply(block).build()

    companion object {
        inline operator fun invoke(block: Builder.() -> Unit): MyStruct {
            val builder = Builder()
            builder.block()
            return builder.build()
        }
    }

    class Builder {
        var foo: String? = null
        var bar: Int = 0
        var baz: Baz? = null
        var yesno: SimpleYesNo? = null
        
        constructor(mystruct: MyStruct): this() {
            this.foo = mystruct.foo
            this.bar = mystruct.bar
            this.baz = mystruct.baz
            this.yesno = mystruct.yesno
        }

        internal fun build(): MyStruct = MyStruct(this)

        // generated for any member shapes that target a StructureShape
        fun baz(block: Baz.Builder.() -> Unit) {
            this.baz = Baz.invoke(block)
        }
    }
}
```

Example usage of the builder(s):

```kotlin
// DSL builder for Kotlin
val mystruct = MyStruct {
    foo = "fooey"
    bar = 12
    baz {
        quux = "foo"
    }
    yesno = SimpleYesNo.YES
}

println(mystruct)
// MyStruct(foo=fooey, bar=12, baz=Baz(quux=foo), yesno=raw)

val mystruct2 = mystruct.copy { 
    foo="copied"
}
println(mystruct2)
// MyStruct(foo=copied, bar=12, baz=Baz(quux=foo), yesno=raw)

println(mystruct3)
// MyStruct(foo=fooey, bar=12, baz=null, yesno=null)
```

Notes:

* This approach favors immutable objects (`val` vs `var`). The Java v2 SDK took the same approach and Kotlin as a
  language strongly favors immutability:
    * [Java v2 SDK notes](https://aws.amazon.com/blogs/developer/aws-sdk-for-java-2-x-released/)
    * [Kotlin coding conventions](https://kotlinlang.org/docs/reference/coding-conventions.html#immutability)
    * [Smart casting](https://kotlinlang.org/docs/reference/typecasts.html#smart-casts) with immutable objects
    * Mutable vs immutable collection interfaces (e.g. `List` vs `MutableList`)
* For each structure shape a builder is provided for constructing immutable objects using a Kotlin DSL approach
* For each structure shape a `copy` function is generated, providing similar functionality available in data classes
* Why not data classes?
    * A data class is a normal class where the compiler generates `hashCode`, `equals`, and `copy`/`componentN`
      functions for you. For this to work though the properties must show up in the default constructor. Even though
      Kotlin can make use of named arguments it doesn't enforce their usage so we can't generate constructors that are
      backwards compatible if new properties are added and a customer is making use of positional arguments.
* The `toString()` methods are examples, the real implementation will differ and take into account the `@sensitive`
  trait
* Methods for `hashCode()` and `equals` will also be generated

#### Union

A [union](https://awslabs.github.io/smithy/spec/core.html#union) is a fixed set of types where only one type is used at
any one time. In Kotlin this maps well to a [sealed class](https://kotlinlang.org/docs/reference/sealed-classes.html).

Example

```
# smithy
union MyUnion {
    bar: Integer,
    foo: String
}
```

```kotlin
sealed class MyUnion

data class Bar(val bar: Int) : MyUnion()
data class Foo(val foo: String): MyUnion()
```

### Service types

Services will generate both an interface and a concrete client implementation provided by the protocol implementation.

Each operation will generate a method with the given operation name and the `input` and `output` shapes of that
operation.

The following example from the Smithy quickstart has been abbreviated. All input/output operation structure bodies have
been omitted as they aren't important to how a service is defined.

```
service Weather {
    version: "2006-03-01",
    resources: [City],
    operations: [GetCurrentTime]
}

@readonly
operation GetCurrentTime {
    output: GetCurrentTimeOutput
}

structure GetCurrentTimeOutput {
    @required
    time: Timestamp
}

resource City {
    identifiers: { cityId: CityId },
    read: GetCity,
    list: ListCities,
    resources: [Forecast],
}

resource Forecast {
    identifiers: { cityId: CityId },
    read: GetForecast,
}

// "pattern" is a trait.
@pattern("^[A-Za-z0-9 ]+$")
string CityId

@readonly
operation GetCity {
    input: GetCityInput,
    output: GetCityOutput,
    errors: [NoSuchResource]
}

structure GetCityInput { ... }

structure GetCityOutput { ...  }

@error("client")
structure NoSuchResource { ... } 

@readonly
@paginated(items: "items")
operation ListCities {
    input: ListCitiesInput,
    output: ListCitiesOutput
}

structure ListCitiesInput { ... }

structure ListCitiesOutput { ... }

@readonly
operation GetForecast {
    input: GetForecastInput,
    output: GetForecastOutput
}

structure GetForecastInput { ... }
structure GetForecastOutput { ... }
```

```kotlin
interface Weather {
    suspend fun getCurrentTime(): GetCurrentTimeOutput

    /**
     * ...
     * 
     * @throws NoSuchResource
     */
    suspend fun getCity(input: GetCityInput): GetCityOutput


    suspend fun listCities(input: ListCitiesInput): ListCitiesOutput


    suspend fun getForecast(input: GetForecastInput): GetForecastOutput
}

class WeatherClient : Weather {

    suspend fun getCurrentTime(): GetCurrentTimeOutput { ... }

    suspend fun getCity(input: GetCityInput): GetCityOutput { ... }

    suspend fun listCities(input: ListCitiesInput): ListCitiesOutput { ... }

    suspend fun getForecast(input: GetForecastInput): GetForecastOutput { ... }
}
```

#### Considerations

##### Why suspend functions?

All service operations are expected to be async operations under the hood since they imply a network call. Making this
explicit in the interface sets expectations up front.

As of Kotlin 1.3 Coroutines are marked stable, they are shipped by default with the stdlib, and it is our belief that
Coroutines are the future of async programming in Kotlin.

As Coroutines become the default choice for async in Kotlin our customers will expect a coroutine compatible API.
Deviating from this with normal threading models is generally incompatible and will create friction.

##### Why not provide both synchronous and asynchronous interfaces?

Coroutines take a different mindset because they are not heavyweight like threads. They have much easier ways to compose
them and share results. One of the design philosophies to follow (w.r.t coroutines) is "let the caller decide". What
this means is when you design an API that is inherently async you let the caller decide how to handle concurrency.
Perhaps they want to process results in the background, or maybe they want to launch several requests and wait for all
of them to complete before continuing, and of course possibly they want to block on each call. You can't account for
each scenario and by the nature of coroutines in Kotlin we don't have to decide up front.

As an example to turn our async client call to a synchronous one in Kotlin is very easy:

```kotlin
val service = WeatherService()

runBlocking {
    val forecast = service.getForecast(input)
}
```

Here is another example where the `getForecast()` and `getCity()` operations happen concurrently and we wait for both
results to complete.

```kotlin
val service = WeatherService()

runBlocking {
    val forecastDef = async { service.getForecast(forecastInput) }
    val cityDef = async { service.getCity(cityInput) } 

    val forecast = forecastDef.await()
    val cityDef = forecastDef.await()
}
```

By providing Coroutine compatible API we can let the caller decide what kind of concurrency makes sense for their use
case/application and compose results as needed.

See [Composing Suspend Functions](https://kotlinlang.org/docs/reference/coroutines/composing-suspending-functions.html)
for more details.

##### Backwards compatibility

This design choice would be a breaking change with the existing `aws-sdk-android` service definitions that are
generated. The current SDK generates an interface and concrete client implementation of that interface as proposed here.
The difference is it generates both synchronous and an asynchronous version. The asynchronous version is based on Java's
[Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) and uses a thread pool executor to
run the request to completion in the background.

We could provide a generated synchronous API that matches the existing by doing the `runBlocking` call for the consumer.
The asynchronous version cannot be made to match if we are to support Coroutines though. See the Java interop discussion
below for more details.

##### Java interop

Suspend functions are not supported directly in Java. Take the following Kotlin code for example:

```kotlin
class FooService {
    suspend fun foo(): Int {
        println("kt: starting foo")
        delay(5000)
        println("kt: foo finished")
        return 12
    }
}
```

Given the above function `foo` in Kotlin, it would appear as a `foo(Continuation<? super Integer> completion)` from
Java. What you get is the Kotlin compiler's de-sugared version of the function which is based on continuations. This is
not easily consumable or understandable. _If we are going to support service definitions that are easily consumable from
Java we will need to provide either a blocking interface, an equivalent async interface based on futures, or both._

```kotlin
// Java compatible async service
class FooServiceAsync {
    private val service: FooService = FooService()

    fun fooAsync(): CompletableFuture<Int> = GlobalScope.future {
        service.foo()
    }

}

// Synchronous FooService client
class FooServiceBlocking {
    private val service: FooService = FooService()

    fun foo(): Int = runBlocking { service.foo() }
}
```

The synchronous interface is easy to generate. It just proxies the Kotlin coroutine service and uses the `runBlocking`
coroutine builder to block the current thread until completion. From Java this looks exactly how you would expect:
`fooService.foo()`.

The asynchronous interface makes use of an
[extra compatibility library](https://github.com/Kotlin/kotlinx.coroutines/tree/master/integration/kotlinx-coroutines-jdk8)
that transforms a suspend function call into a `CompletableFuture`. It launches the coroutine into the GlobalScope and
returns a (completable) future that works as you would expect. This approach requires
`org.jetbrains.kotlinx:kotlinx-coroutines-jdk8` as a dependency which is _Android API level 24._ 

#### Summary

1. Coroutines work fine on Android. Suspend functions were tested with Ktor HTTP client on an API level 16 (Jelly Bean)
   virtual device without issue.
1. Blocking calls based on coroutines work fine (due to 1)
1. An async interface based on `CompletableFuture` is API 24+ compatible.

#### Suggestions

**Suggestion**: We should take a "Kotlin first" approach and provide the suspend based coroutine API as the "primary"
client interface.

**Response**: Why? SDKs should strive to be idiomatic for the target language. Also Google has on numerous occasions
([example](https://techcrunch.com/2019/05/07/kotlin-is-now-googles-preferred-language-for-android-app-development/))
that Kotlin is the preferred language going forward and that "Android will become increasingly Kotlin-first".

### Resource Types

Each resources will be processed for each of the corresponding lifecycle operations as well as the non-lifecycle
operations.

Every operation, both lifecycle and non-lifecycle, will generate a method on the service class to which the resource
belongs.

This will happen recursively since resources can have child resources.

See the Service section which has a detailed example of how resources show up on a service.

### Traits

#### Type refinement traits

##### `box` trait

Indicates that a shape is boxed which means the member may or may not contain a value and that the member has no default
value.

NOTE: all shapes other than primitives are always considered boxed in the Smithy spec

```
structure Foo {
    @box
    bar: integer
}
```

```kotlin
class Foo {
    var bar: Int? = null
}
```

##### `deprecated` trait

Will generate the equivalent code for the shape annotated with Kotlin's `@Deprecated` annotation.

```
@deprecated
structure Foo

@deprecated(message: "no longer used", since: "1.3")
structure Bar
```

```kotlin
@Deprecated
class Foo

@Deprecated(message = "no longer used; since 1.3")
class Bar
```

##### `error` trait

The error trait will be processed as an exception type in Kotlin. This requires support from the client-runtime lib.

See [Modeled errors](kotlin-modeled-errors.md)

#### Constraint traits

##### `enum` trait

Enums will be modeled as sealed classes. The advantage of a sealed class is in retention of unknown values with little
to no loss of usability on the use of the type. The compiler warns on non-exhaustive matching and the syntax of `when`
matches uses the form `is XYZ`.

When no `name` is provided the sealed class variant name will be the same as the value, otherwise the Kotlin SDK will
use the provided enum name.

The value will be stored as an abstract property on the sealed class and each variant will have to override it. This
allows enums to be applied to other types in the future if Smithy
[evolves to allow it](https://github.com/awslabs/smithy/issues/98).

```
@enum("YES": {}, "NO": {})
string SimpleYesNo

@enum("Yes": {name: "YES"}, "No": {name: "NO"})
string TypedYesNo
```

Simple example:

```kotlin
sealed class SimpleYesNo {
    abstract val value: String

    object Yes: SimpleYesNo() {
        override val value: String = "YES"
        override fun toString(): String = value
    }

    object No: SimpleYesNo() {
        override val value: String = "NO"
        override fun toString(): String = value
    }

    data class SdkUnknown(override val value: String): SimpleYesNo() {
        override fun toString(): String = "SdkUnknown($value)"
    }

    companion object {
        /**
         * Convert a raw string to an enum constant using either the constant name or it's value
         */
        fun fromValue(str: String): SimpleYesNo = when(str) {
            "YES" -> Yes
            "NO" -> No
            else -> SdkUnknown(str)
        }

        /**
         * Get a list of all possible variants
         */
        fun values(): List<SimpleYesNo> = listOf(Yes, No)
    }
}

sealed class TypedYesNo {
    abstract val value: String

    object Yes: TypedYesNo() {
         override val value: String = "Yes"
         override fun toString(): String = value
    }

    object No: TypedYesNo() {
         override val value: String = "No"
         override fun toString(): String = value
    }

    data class SdkUnknown(override val value: String): TypedYesNo() {
        override fun toString(): String = value
    }

    companion object {
        fun fromValue(str: String): TypedYesNo = when(str) {
            "Yes" -> Yes
            "No" -> No
            else -> SdkUnknown(str)
        }

        fun values(): List<TypedYesNo> = listOf(Yes, No)
    }
}
```

More complex example:

```
@enum(
    t2.nano: {
        name: "T2_NANO",
        documentation: """
            T2 instances are Burstable Performance
            Instances that provide a baseline level of CPU
            performance with the ability to burst above the
            baseline.""",
        tags: ["ebsOnly"]
    },
    t2.micro: {
        name: "T2_MICRO",
        documentation: """
            T2 instances are Burstable Performance
            Instances that provide a baseline level of CPU
            performance with the ability to burst above the
            baseline.""",
        tags: ["ebsOnly"]
    },
    m256.mega: {
        name: "M256_MEGA",
        deprecated: true
    }
)
string MyString
```

```kotlin
sealed class MyString {
    abstract val value: String

    /**
     * T2 instances are Burstable Performance
     * Instances that provide a baseline level of CPU
     * performance with the ability to burst above the
     * baseline.
     */
    object T2Micro : MyString() {
        override val value: String = "t2.micro"
        override fun toString(): String = value
    }

    object T2Nano : MyString() {
        override val value: String = "t2.nano"
        override fun toString(): String = value
    }
    
    @Deprecated("deprecated")
    object M256Mega : MyString() {
        override val value: String = "m256.mega"
        override fun toString(): String = value
    }

    data class SdkUnknown(override val value: String) : MyString() {
        override fun toString(): String = value
    }

    companion object {
        /**
         * Convert a raw value to one of the sealed variants or [SdkUnknown]
         */
        fun fromValue(str: String): MyString = when(str) {
            "t2.micro" -> T2Micro
            "t2.nano" -> T2Nano
            "m256.mega" -> M256Mega
            else -> SdkUnknown(str)
        }

        /**
         * Get a list of all possible variants
         */
        fun values(): List<MyString> = listOf(
            T2Micro,
            T2Nano,
            M256Mega
        )
    }
}
```

###### Serialization

Serialization will need to make use of the given `value` of the enum rather than the name of the enum constant. 

###### Unknown enum names

The Smithy core spec indicates that unknown enum values need to be handled as well.

> Consumers that choose to represent enums as constants SHOULD ensure that unknown enum names returned from a service do
> not cause runtime failures.

Each sealed class is generated with an
`SdkUnknown` variant which is used to deal with forwards compatibility.

##### `idRef` trait

Not processed

##### `length` trait

Not processed

##### `pattern` trait

Not processed

##### `private` trait

Not processed

##### `range` trait

Not processed

##### `required` trait

Not processed

##### `uniqueItems` trait

Not processed

#### Behavior traits

##### `idempotentcyToken` trait

Not processed

##### `idempotent` trait

Not processed

##### `readonly` trait

Not processed

##### `retryable` trait

This trait influences errors, see the `error` trait for how it will be handled.

##### `paginated` trait

See [Pagination](pagination.md).

#### Resource traits

##### `references` trait

Not processed

##### `resourceIdentifier` trait

Not processed

#### Protocol traits

##### `protocols` trait

Inspected to see if the protocol is supported by the code generator/client-runtime. If no protocol is supported codegen
will fail.

The `auth` peroperty of this trait will be inspected just to confirm at least one of the authentication schemes is
supported.

All of the built-in HTTP authentication schemes will be supported by being able to customize the request headers.

##### `auth` trait

Processed the same as the `auth` property of the `protocols` trait. 

##### `jsonName` trait

Influences serialization/deserialization

##### `mediaType` trait

The media type trait SHOULD influence the HTTP Content-Type header if not already set.

##### `timestampFormat` trait

Influences serialization/deserialization

#### Documentation traits

##### `documentation` trait

All top level classes, enums, and their members will be generated with the given documentation.

##### `examples` trait

Not processed

##### `externalDocumentation` trait

Processed the same as the `documentation` trait. The link will be processed appropriately for the target documentation
engine (e.g. [dokka](https://github.com/Kotlin/dokka)).

##### `sensitive` trait

Influences the generated `toString()` method to ignore sensitive values

##### `since` trait

Not processed

##### `tags` trait

Not processed

##### `title` trait

Combined with the generated documentation as the first text to show up for a service or resource.

#### Endpoint traits

##### `endpoint` trait

See [Endpoint resolution](endpoint-resolution.md)

##### `hostLabel` trait

Influences endpoint resolution

## Event stream spec

Binary streams: [Kotlin (binary) streaming request/response bodies](kotlin-binary-streaming-request-response-bodies.md)

Event streams: [Event streams](event-streams.md)

# Revision history

* 11/15/2021 - Update code snippets from builder refactoring
* 5/27/2021 - Initial upload
