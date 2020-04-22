# Kotlin Smithy SDK 

## Core Spec

Reference the Smithy [Core Spec](https://awslabs.github.io/smithy/spec/core.html)

### Identifiers and Naming
Kotlin keywords can be found [here](https://kotlinlang.org/docs/reference/keyword-reference.html). Kotlin has both hard keywords and soft keywords (context sensitive).

The list of hard keywords that can never be identifiers in Kotlin is:
* `as`
* `as?`
* `break`
* `class`
* `continue`
* `do`
* `else`
* `false`
* `for`
* `fun`
* `if`
* `in`
* `!in`
* `interface`
* `is`
* `!is`
* `null`
* `object`
* `package`
* `return`
* `super`
* `this`
* `throw`
* `true`
* `try`
* `typealias`
* `typeof`
* `val`
* `var`
* `when`
* `while`

### Simple Shapes


|Smithy Type| Description                                                       | Kotlin Type
|-----------|-------------------------------------------------------------------|------------------------
|blob       | Uninterpreted binary data                                         | ByteArray
|boolean    | Boolean value type                                                | Boolean
|string     | UTF-8 encoded string                                              | String
|byte       | 8-bit signed integer ranging from -128 to 127 (inclusive)         | Byte
|short      | 16-bit signed integer ranging from -32,768 to 32,767 (inclusive)  | Short
|integer    | 32-bit signed integer ranging from -2^31 to (2^31)-1 (inclusive)  | Int
|long       | 64-bit signed integer ranging from -2^63 to (2^63)-1 (inclusive)  | Long
|float      | Single precision IEEE-754 floating point number                   | Float
|double     | Double precision IEEE-754 floating point number                   | Double
|bigInteger | Arbitrarily large signed integer                                  | **TBD**
|bigDecimal | Arbitrary precision signed decimal number                         | **TBD**
|timestamp  | Represents an instant in time with no UTC offset or timezone. The serialization of a timestamp is determined by a protocol. | TBD (Kotlin 1.4 should bring a Datetime lib that is MPP compatible, if we can wait for that we should)
|document   | Unstable Represents an untyped JSON-like value that can take on one of the following types: null, boolean, string, byte, short, integer, long, float, double, an array of these types, or a map of these types where the key is string. | Custom type provided by client runtime

**QUESTION**: We should support the `document` type but perhaps we can wait until it's marked stable to do anything with it? At the very least we should annotate the type as unstable if it's going to be in a public API
**QUESTION**: The JVM has big number support but it is not MP (multi-platform) compatible. This is probably ok depending on how we structure the relationship between the generated SDK and the client runtime which will support MP.

#### `document` Type

The `document` type is an  untyped JSON-like value that can take on the following types: null, boolean, string, byte, short, integer, long, float, double, an array of these types, or a map of these types where the key is string.


This type is best represented as a Sum type (sealed class is closest we can get in Kotlin). See the [JSON type](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElement.kt) from the kotlinx.serialization lib for an example on which the following is derived. We should provide our own type but we may be able to internally deal with serialization by going through the one from the kotlinx.serialization library.


```kotlin
package com.amazonaws.smithy.runtime


/**
 * Class representing a Smithy Document type.
 * Can be a [SmithyNumber], [SmithyBool], [SmithyString], [SmithyNull], [SmithyArray], or [SmithyMap]
 */
sealed class Document {
    /**
     * Checks whether the current element is [SmithyNull]
     */
    val isNull: Boolean
        get() = this == SmithyNull
}

/**
 * Class representing document `null` type.
 */
object SmithyNull : Document() {
    override fun toString(): String = "null"
}

/**
 * Class representing document `bool` type
 */
data class SmithyBool(val value: Boolean): Document() {
    override fun toString(): String = when(value) {
        true -> "true"
        false -> "false"
    }
}

/**
 * Class representing document `string` type
 */
data class SmithyString(val value: String) : Document() {
    override fun toString(): String {
        return "\"$value\""
    }
}


/**
 * Class representing document numeric types.
 *
 * Creates a Document from a number literal: Int, Long, Short, Byte, Float, Double
 */
class SmithyNumber(val content: Number) : Document() {

    /**
     * Returns the content as a byte which may involve rounding
     */
    val byte: Byte get() = content.toByte()

    /**
     * Returns the content as a int which may involve rounding
     */
    val int: Int get() = content.toInt()

    /**
     * Returns the content as a long which may involve rounding
     */
    val long: Long get() = content.toLong()

    /**
     * Returns the content as a float which may involve rounding
     */
    val float: Float get() = content.toFloat()

    /**
     * Returns the content as a double which may involve rounding
     */
    val double: Double get() = content.toDouble()

    override fun toString(): String = content.toString()
}


/**
 * Class representing document `array` type
 */
data class SmithyArray(val content: List<Document>): Document(), List<Document> by content{
    /**
     * Returns [index] th element of an array as [SmithyNumber] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getNumber(index: Int) = content[index] as? SmithyNumber

    /**
     * Returns [index] th element of an array as [SmithyBool] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getBoolean(index: Int) = content[index] as? SmithyBool

    /**
     * Returns [index] th element of an array as [SmithyString] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getString(index: Int) = content[index] as? SmithyString

    /**
     * Returns [index] th element of an array as [SmithyArray] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getArray(index: Int) = content[index] as? SmithyArray

    /**
     * Returns [index] th element of an array as [SmithyMap] if the element is of that type or null if not.
     *
     * @throws IndexOutOfBoundsException if there is no element with given index
     */
    fun getMap(index: Int) = content[index] as? SmithyMap

    override fun toString(): String = content.joinToString( separator = ",", prefix = "[", postfix = "]" )
}

/**
 * Class representing document `map` type
 *
 * Map consists of name-value pairs, where the value is an arbitrary Document. This is much like a JSON object.
 */
data class SmithyMap(val content: Map<String, Document>): Document(), Map<String, Document> by content {

    /**
     * Returns [SmithyNumber] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getNumber(key: String): SmithyNumber? = getValue(key) as? SmithyNumber

    /**
     * Returns [SmithyBool] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getBoolean(key: String): SmithyBool? = getValue(key) as? SmithyBool

    /**
     * Returns [SmithyString] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getString(key: String): SmithyString? = getValue(key) as? SmithyString

    /**
     * Returns [SmithyArray] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getArray(key: String): SmithyArray? = getValue(key) as? SmithyArray

    /**
     * Returns [SmithyMap] associated with given [key] or `null` if element is not present or has a different type
     */
    fun getMap(key: String): SmithyMap? = getValue(key) as? SmithyMap


    override fun toString(): String {
        return content.entries.joinToString(
            separator = ",",
            prefix = "{",
            postfix = "}",
            transform = {(k, v) -> """"$k":$v"""}
        )
    }

}

fun Boolean.toDocument() = SmithyBool(this)
fun Number.toDocument() = SmithyNumber(this)
fun String.toDocument() = SmithyString(this)


/**
 * DSL builder for a [SmithyArray]
 */
class DocumentArrayBuilder internal constructor() {
    internal val content: MutableList<Document> = mutableListOf()

    /**
     * Adds [this] value to the current [SmithyArray] as [SmithyString]
     */
    operator fun String.unaryPlus() {
        content.add(SmithyString(this))
    }

    /**
     * Adds [this] value to the current [SmithyArray] as [SmithyBool]
     */
    operator fun Boolean.unaryPlus() {
        content.add(SmithyBool(this))
    }

    /**
     * Adds [this] value to the current [SmithyArray] as [Document]
     */
    operator fun Document.unaryPlus() {
        content.add(this)
    }

    /**
     * Convenience function to wrap raw numeric literals
     *
     * Use as `+n()` inside of [documentArray] builder init().
     */
    fun n(number: Number): SmithyNumber = SmithyNumber(number)

}

/**
 * Builds [SmithyArray] with given [init] builder.
 *
 * NOTE: raw numeric types need to be wrapped as a [SmithyNumber]. Use the [DocumentArrayBuilder::a] builder
 * as a shorthand.
 */
fun documentArray(init: DocumentArrayBuilder.() -> Unit): Document {
    val builder = DocumentArrayBuilder()
    builder.init()
    return SmithyArray(builder.content)
}


/**
 * DSL builder for a [Document] as a [SmithyMap]
 */
class DocumentBuilder internal constructor() {
    internal val content: MutableMap<String, Document> = linkedMapOf()

    /**
     * Adds given [value] as [SmithyBool] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Boolean) {
       require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] as [SmithyNumber] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Number) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] as [SmithyString] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: String) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value.toDocument()
    }

    /**
     * Adds given [value] to the current [SmithyMap] with [this] as a key
     */
    infix fun String.to(value: Document) {
        require(content[this] == null) {"Key $this is already registered in builder"}
        content[this] = value
    }

}

/**
 * Builds [Document] with given [init] builder.
 *
 * ```
 * val doc = document {
 *     "foo" to 1
 *     "baz" to document {
 *         "quux" to documentArray {
 *             +n(202L)
 *             +n(12)
 *             +true
 *             +"blah"
 *         }
 *     }
 *     "foobar" to document {
 *         "nested" to "a string"
 *         "blerg" to documentArray {
 *             +documentArray {
 *                 +n(2.02)
 *              }
 *         }
 *     }
 * }
 * ```
 *
 * This generates the following JSON:
 * {"foo":1,"baz":{"quux":[202,12,true,"blah"]},"foobar":{"nested":"a string","blerg":[[2.02]]}}
 */
fun document(init: DocumentBuilder.() -> Unit): Document {
    val builder = DocumentBuilder()
    builder.init()
    return SmithyMap(builder.content)
}

```

Example usage of building a doc or processing one

```kotlin
fun foo() {
    val doc = document {
        "foo" to 1
        "baz" to document {
            "quux" to documentArray {
                +n(202L)
                +n(12)
                +true
                +"blah"
            }
        }
        "foobar" to document {
            "nested" to "a string"
            "blerg" to documentArray {
                +documentArray {
                    +n(2.02)
                }
            }
        }
    }
    println(doc)

    processDoc(doc)
}

fun processDoc(doc: Document) {
    when(doc) {
        is SmithyNumber -> println("number: $doc")
        is SmithyBool -> println("bool: ${doc.value}")
        is SmithyString -> println("str: ${doc.value}")
        is SmithyNull -> println("str: $doc")
        is SmithyArray-> {
            println("array")
            for (d in doc) processDoc(d)
        }
        is SmithyMap -> {
            println("map")
            for((k,d) in doc) {
                print("$k: ")
                processDoc(d)
            }
        }
    }

}

```



### Aggregate types


| Smithy Type | Kotlin Type
|-------------|-------------
| list        | List
| set         | Set
| map         | Map
| structure   | *Class
| union       | *sealed class


#### Structure

A [structure](https://awslabs.github.io/smithy/spec/core.html#structure) type represents a fixed set of named heterogeneous members. In Kotlin this can be represented
as either a normal class or a data class. 

Non boxed member values will be defaulted according to the spec: `The default value of a byte, short, integer, long, float, and double shape that is not boxed is zero`

```
list MyList {
    member: String
}

structure Foo {
    bar: String,
    baz: Integer,
    quux: MyList
}
```

##### ALTERNATIVE 1

```kotlin
class Foo {
    var bar: String? = null
    var baz: Int = 0
    var quux: List<String>? = null
}
```

##### ALTERNATIVE 2

```kotlin
data class Foo(
    var bar: String? = null
    var baz: Int = 0
    var quux: List<String>? = null
)
```


The problem with this approach is readability since the number of constructor arguments could be potentially large. The form in alternative 1 can easily be initialized with the following code which makes this alternate less useful.

```kotlin
val f = Foo().apply {
    bar = "bar"
    baz = 12
    quux = listOf("foo", "bar", "quux")
}
```

#### Union

A [union](https://awslabs.github.io/smithy/spec/core.html#union) is a fixed set of types where only one type is used at any one time. In Kotlin this maps well to a [sealed class](https://kotlinlang.org/docs/reference/sealed-classes.html)

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

Services will generate both an interface as well as a concrete client implementation.

Each operation will generate a method with the given operation name and the `input` and `output` shapes of that operation.


The following example from the Smithy quickstart has been abbreviated. All input/output operation structure bodies have been omitted as they aren't important to how a service is defined.

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

1. Why `suspend` functions? 

All service operations are expected to be async operations under the hood since they imply a network call. Making this explicit in the interface sets expectations up front.

As of Kotlin 1.3 Coroutines are marked stable, they are shipped by default with the stdlib, and it is our belief that Coroutines are the future of async programming in Kotlin.

As Coroutines become the default choice for async in Kotlin our customers will expect a coroutine compatible API. Deviating from this with normal threading models is generally incompatible and will create friction.

As such choosing a Coroutine compatible API is "Customer Obsessed" as we strive to provide the most idiomatic API for the target ecosystem. This design is also "Think Big" as we define a bold direction for the Android SDK. 

2. Why not provide both synchronous and asynchronous interfaces?

Coroutines take a different mindset because they are not heavyweight like threads. They have much easier ways to compose them and share results. One of the design philosophies to follow (w.r.t coroutiens) is `let the caller decide`. What this means is when you design an API that is inherently async you let the caller decide how to handle concurrency. Perhaps they want to process results in the background, or maybe they want to launch several requests and wait for all of them to complete before continuing, and of course possibly they want to block on each call. You can't account for each scenario and by the nature of Coroutines in Kotlin we don't have to decide up front. 


As an example to turn our async client call to a synchronous one in Kotlin is very easy

```

val service = WeatherService()

runBlocking {
    val forecast = service.getForecast(input)
}

```

Here is another example where the `getForecast()` and `getCity()` operations happen concurrently and we wait for both results to complete. 

```
val service = WeatherService()

runBlocking {
    val forecastDef = async { service.getForecast(forecastInput) }
    val cityDef = async { service.getCity(cityInput) } 

    val forecast = forecastDef.await()
    val cityDef = forecastDef.await()
}

```


By providing Coroutine compatible API we can let the caller decide what kind of concurrency makes sense for their use case/application and compose results as needed. 


See [Composing Suspend Functions](https://kotlinlang.org/docs/reference/coroutines/composing-suspending-functions.html) for more details.


3. Backwards Compatibility


This design choice would be a breaking change with the existing `aws-sdk-android` service definitions that are generated. The current SDK generates an interface and concrete client implementation of that interface as proposed here. The difference is it generates both synchronous and an asyncronous version. The asyncronous version is based on Java's [Future](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) and uses a thread pool executor to run the request to completion in the background.

We could provide a generated syncronous API that matches the existing by doing the `runBlocking` call for the consumer but there is little value in doing so. The asyncronous version cannot be made to match if we are to support Coroutines though.

The recommendation would be to make the breaking change and embrace Coroutines for the reasons outlined since this is a new SDK.


### Resource types

Each resources will be processed for each of the corresponding lifecycle operations as well as the non-lifecycle operations. 

Every operation, both lifecycle and non-lifecycle, will generate a method on the service class to which the resource belongs. 

This will happen recursively since resources can have child resources. 

See the Service section which has a detailed example of how resources show up on a service.


### Traits

### Type Refinement Traits

#### `box` trait

Indicates that a shape is boxed which means the member may or may not contain a value and that the member has no default value.

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

**QUESTION**: If all non-primitive types (e.g. String, Structure, List, etc) are considered boxed should they all be generated as nullable in Kotlin?
e.g.

```
structure Baz {
    quux: integer
}

structure Foo {
    bar: String,
    baz: Baz
}

```

```kotlin
class Baz(var quux: Int = 0)

class Foo {
    var bar: String? = null
    var baz: Baz? = null
}

```


#### `deprecated` trait

Will generate the equivalent code for the shape annotated with Kotlin's `@Deprecated` annotation.

```
@deprecated
structure Foo

@deprecated(message: "no longer used", since: "1.3")
```

```kotlin
@Deprecated
class Foo

@Deprecated(message = "no longer used; since 1.3")
class Foo
```

#### `error` trait

The `error` trait will be processed as an exception type in Kotlin. This requires support from the client-runtime lib. See "Exceptions" in the Appendix.


Note the Smithy core spec indicates: `The message member of an error structure is special-cased. It contains the human-readable message that describes the error. If the message member is not defined in the structure, code generated for the error may not provide an idiomatic way to access the error message (e.g., an exception message in Java).`

If present these should be translated to the `ServiceException::errorMessage` property.


The `httpError` trait should not need additional processing assuming the HTTP response itself is exposed in someway on `ServiceException`. 


```
@error("server")
@httpError(501)
structure NotImplemented {}

@error("client")
@retryable
structure ThrottlingError {
    @required
    message: String,
}

```


```kotlin
import software.amazon.smithy.kotlin.core.ServiceException

class NotImplementedException: ServiceException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    override val serviceName: String = "MyService"

    override val errorType: ErrorType = ErrorType.Server

    override val isRetryable: Boolean = false
}


class ThrottlingError : ServiceException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    override val serviceName: String = "MyService"

    override val errorType: ErrorType = ErrorType.Client

    override val isRetryable: Boolean = true
}


```


### Constraint traits

#### `enum` trait

Kotlin has first class support for enums and the SDK should make use of them to provide a type safe interface.

When no `name` is provided the enum name will be the same as the value, otherwise the Kotlin SDK will use the provided enum name.

The value will be stored as an additional property on the enum and passed in the constructor. This allows enums to be applied to other types in the future if Smithy [evolves to allow it](https://github.com/awslabs/smithy/issues/98).


```
@enum("YES": {}, "NO": {})
string SimpleYesNo

@enum("Yes": {name: "YES"}, "No": {name: "NO"})
string TypedYesNo
```

```kotlin
enum class SimpleYesNo(val value: String) {
    YES("YES")
    NO("NO")
}

enum class TypedYesNo(val value: String) {
    YES("Yes")
    NO("No")
}
```


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
enum class MyString(val value: String) {

    /**
     * T2 instances are Burstable Performance Instances that provide a baseline level of CPU performance with the ability to burst above the baseline.
     */
    T2_NANO("t2.nano"),

    /**
     * T2 instances are Burstable Performance Instances that provide a baseline level of CPU performance with the ability to burst above the baseline.
     */
    T2_MICRO("t2.micro"),

    @Deprecated
    M256_MEGA("m256.mega"),
}
```


#### Considerations

**Serialization**


Serialization will need to make use of the given `value` of the enum rather than the name of the enum constant. 

See the following for examples using kotlinx.serialization:
* https://ahsensaeed.com/enum-class-serialization-kotlinx-serialization-library/
* https://github.com/Kotlin/kotlinx.serialization/issues/31#issuecomment-488572539

**Unknown Enum Names**

The Smithy core spec indicates that unknown enum values need to be handled as well. 

```
Consumers that choose to represent enums as constants SHOULD ensure that unknown enum names returned from a service do not cause runtime failures.
```

There isn't a great way to do that automatically yet. 
See: https://github.com/Kotlin/kotlinx.serialization/issues/90


The way to do that currently would be to always generate enums with an `SDK_UNKNOWN` enum constant and set the value to that during deserialization if one of the values fails to match.

**QUESTION**: Is it important to capture what the unrecognized value was? If so we need to rethink the enum representation probably.


#### `idRef` trait
Not processed

#### `length` trait
**TODO**
**QUESTION** I don't even see where these constraints (length, range, pattern, etc) are processed in the smithy-typescript/smithy-go code generators. Are they not implemented?

#### `pattern` trait
**TODO**

#### `private` trait
Not processed 

#### `range` trait
**TODO**

#### `required` trait

```
struct Foo {
    @required
    bar: String
}
```

##### ALTERNATIVE 1

All members marked `required` could show up in the constructor of the class and not be treated as nullable

```kotlin
class Foo(@Required var bar: String) {
}
```

##### ALTERNATIVE 2

Generate the code normally but mark it with the kotlinx.serialization `@Required` annotation.

```kotlin
class Foo {
    @Required
    var bar: String? = null
}
```


NOTE: Regardless of which way we go we should mark the field with the `@Required` annotation. This forces serialization to be mandatory and always present/expected in serialized form.
See [kotlinx.serialization annotations](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/examples.md#annotations). If we don't utilize kotlinx.serialization then we need to account for `required` fields in whatever our chosen serde implementation is.

#### `uniqueItems` trait
**TODO**

### Behavior traits

#### `idempotentcyToken` trait
**TODO** The spec states that `clients MAY automatically provide a value`. This could be interpreted to provide a default UUID and allow it to be overridden.

#### `idempotent` trait

Not processed

**FUTURE** It may be worthwhile generating documentation that indicates the operation is idempotent.

#### `readonly` trait

Not processed

#### `retryable` trait

This trait influences errors, see the `error` trait for how it will be handled.


#### `paginated` trait

Not processed

**QUESTION** If I understood correctly the members targeted by `inputToken`/`outputToken`/`items` all MUST be defined in the input/output structures. As in the generator isn't expected to create those members if they don't exist?
**QUESTION** Would it be useful to process the trait for documentation purposes on those class members or is it expected that those fields would already have attached documentation traits?

### Resource traits

#### `references` trait
Not processed

#### `resourceIdentifier` trait
Not processed

### Protocol traits

#### `protocols` trait

Inspected to see if the protocol is supported by the code generator/client-runtime. If no protocol is supported codegen will fail.

The `auth` peroperty of this trait will be inspected just to confirm at least one of the authentication schemes is supported.

All of the built-in HTTP authentication schemes will be supported by being able to customize the request headers.


**QUESTION** Should we have codegen validate the request has one of the auth schemes expected for a particular operation before sending the request? 
    * (AJT) - I think this would be nice but not necessary, at least out the gate, presumably we'll get an error response from the server that will make sense.

#### `auth` trait

Processed the same as the `auth` property of the `protocols` trait. 

#### `jsonName` trait

The generated class member will have the `@SerialName("...")` annotation added to the property. 

* **TODO**: Assumes we utilize kotlinx.serialization to handle JSON serialization. Regardless of which way we go this trait will influence serde.

#### `mediaType` trait

The media type trait SHOULD influence the HTTP Content-Type header if not already set.

**QUESTION** Can we ship an initial version without supporting this (as long as the client has the ability to set the Content-Type header)? 


#### `timestampFormat` trait

**TODO** - This depends on the datetime lib we use and how we plug serde into everything. Roughly this just affects the serde step of any `timestamp` shape.

### Documentation traits

#### `documentation` trait

All top level classes, enums, and their members will be generated with the given documentation.

#### `examples` trait

Not processed

**FUTURE** We probably should process this but I think it's ok to put it lower priority

#### `externalDocumentation` trait

Processed the same as the `documentation` trait. The link will be processed appropriately for the target documentation engine (e.g. [dokka](https://github.com/Kotlin/dokka)).

#### `sensitive` trait

Not processed

#### `since` trait

Not processed

**FUTURE** We should probably process this into the generated documentation at least.

#### `tags` trait

Not processed

#### `title` trait

Combined with the generated documentation as the first text to show up for a service or resource.


### Endpoint traits

#### `endpoint` trait
**TODO**

#### `hostLabel` trait
**TODO**


# HTTP Protocol Bindings

**TODO** We have decided to split the design and implementation. HTTP protocol bindings will be fleshed out further in an initial POC.

# Event Stream Spec

The design of event stream bindings is still in progress. It will be reviewed separately.


# Appendix

## Client Runtime (multiplatform)

Service clients generated by the smithy-kotlin generator requires support from a runtime package. One of the areas of exploration is whether we can share a large majority of a client runtime using Kotlin Multiplatform. It is not yet clear whether this is doable or what problems we might hit. The general consensus at the moment is that we have to design such a package for the Kotlin SDK anyway so it's worthwhile to pursue and see what benefits we can get out of going that route. This is a two way door since we only risk some time to try and make multiplatform work and if it doesn't we can always go back and implement a client runtime in swift. 


The following gives an example of how all these packages relate to each-other.

```



smithy-kotlin  ----R----> client-rt (MPP)
smithy-swift   ----R----> client-rt (MPP)


aws-sdk-kotlin ----C---> smithy-kotlin 
              \----R---> client-rt (MPP)
              \----R---> aws-client-rt (MPP)

aws-sdk-swift  ----C---> smithy-swift
              \----R---> client-rt (MPP)
              \----R---> aws-client-rt (MPP)


Legend:
--------
The `C` and `R` denote compile time vs runtime dependencies.
client-rt = Generic smithy client runtime, (protocol(s), orchestration, etc)
aws-client-rt = AWS specific client runtime, credentials, Sigv4 signing, etc

```


More than likely we expect that not everything will be doable in Kotlin multiplatform or be too much effort (e.g. serialization). In which case the multiplatform client runtime would have to be supplemented with additional packages for the target language/environment. That might look something more like this:

```
smithy-kotlin  ----R----> client-rt (MPP)
              \----R----> client-rt-android (Kotlin)

smithy-swift   ----R----> client-rt (MPP)
              \----R----> client-rt-ios (Swift)

aws-sdk-kotlin ----C---> smithy-kotlin 
              \----R---> client-rt (MPP)
              \----R---> client-rt-android (Kotlin)
              \----R---> aws-client-rt (MPP)

aws-sdk-swift  ----C---> smithy-swift
              \----R---> client-rt-ios (Swift)
              \----R---> aws-core (MPP)
              \----R---> aws-client-rt (MPP)

```


The Smithy team indicated we probably want the runtime package to live with the code generator so that code reviews can be more seamless and not coordinated across repos. This makes sense but if the client runtime is a multiplatform package there will end up being some cross repo coordination at some point. I've shown a rough example below of what that structure might look like if we just assume it lives with the `smithy-kotlin` code generator.


```
smithy-kotlin/

    client-runtime/
        aws-smithy-clientrt-core/                        Package: com.amazonaws.clientrt    (multiplatform core package)
            commonMain/
                smithy/                       - smithy types (e.g. Document)
                Exceptions.kt                 - Generic client/service exceptions
                http/
                ...
             androidMain/
                ...
             iosMain/
                ...

        aws-smithy-clientrt-android/                      Package: com.amazonaws.clientrt.android

    smithy-kotlin-codegen/
        smithy code generator for kotlin
    smithy-kotlin-codegen-test/
        integration test for smithy-kotlin-codegen


NOTES:
    * A `aws-smithy-clientrt-ios` would be a pure Swift package that builds on top of or supplements MPP clientrt-core.
     It would provide the additional things that can't exist in the MPP core and be analogous to `aws-smithy-clientrt-android`
    * The rough rule for what can go in clientrt-core is anything concrete that doesn't use generics (or is very limited generics: See the limitations of generics in Kotlin MPP)
```


## Exceptions

The client runtime lib will expose the common exception types that all generated service/operation errors will be translated to (and inherit from).

### Background: Current aws-sdk-android exception hierarchy

```
java.lang.Object
   java.lang.Throwable
       java.lang.Exception
           java.lang.RuntimeException
               com.amazonaws.AmazonClientException
                   com.amazonaws.AmazonServiceException 
                       ... all service specific exceptions/errors
```


`AmazonClientException`

Base exception class for any errors that occur while attempting to use an AWS client to make service calls to Amazon Web Services.

Error responses from services will be handled as AmazonServiceExceptions. This class is primarily for errors that occur when unable to get a response from a service, or when the client is unable to understand a response from a service. For example, if a caller tries to use a client to make a service call, but no network connection is present, an AmazonClientException will be thrown to indicate that the client wasn't able to successfully make the service call, and no information from the service is available.

Callers should typically deal with exceptions through AmazonServiceException, which represent error responses returned by services. AmazonServiceException has much more information available for callers to appropriately deal with different types of errors that can occur.


The API currently looks like this:

```
// Creates a new AmazonClientException with the specified message.
AmazonClientException(java.lang.String message)

// Creates a new AmazonClientException with the specified message, and root cause.
AmazonClientException(java.lang.String message, java.lang.Throwable t)

// Create an AmazonClientException with an exception cause.
AmazonClientException(java.lang.Throwable throwable)

// Returns a hint as to whether it makes sense to retry upon this exception
boolean isRetryable()
```


`AmazonServiceException`

Extension of AmazonClientException that represents an error response returned by an Amazon web service. Receiving an exception of this type indicates that the caller's request was correctly transmitted to the service, but for some reason, the service was not able to process it, and returned an error response instead.

AmazonServiceException provides callers several pieces of information that can be used to obtain more information about the error and why it occurred. In particular, the errorType field can be used to determine if the caller's request was invalid, or the service encountered an error on the server side while processing it.


The API currently looks like this:
```java
enum class ErrorType {
    Client, Service, Unknown
}


// Constructs a new AmazonServiceException with the specified message.
AmazonServiceException(java.lang.String errorMessage)

// Constructs a new AmazonServiceException with the specified message and exception indicating the root cause.
AmazonServiceException(java.lang.String errorMessage, java.lang.Exception cause)

// Returns the AWS error code represented by this exception.
String 	getErrorCode()

// Sets the AWS error code represented by this exception.
void 	setErrorCode(java.lang.String errorCode)

String 	getErrorMessage() 

void 	setErrorMessage(java.lang.String errorMessage) 

// Indicates who is responsible for this exception (caller, service, or unknown).
AmazonServiceException.ErrorType 	getErrorType()

// Sets the type of error represented by this exception (sender, receiver, or unknown), indicating if this exception was the caller's fault, or the service's fault.
void 	setErrorType(AmazonServiceException.ErrorType errorType)

String 	getMessage() 

// Returns the AWS request ID that uniquely identifies the service request the caller made.
String 	getRequestId()

// Sets the AWS requestId for this exception.
void 	setRequestId(java.lang.String requestId)

// Returns the name of the service that sent this error response.
String 	getServiceName()

// Sets the name of the service that sent this error response.
void 	setServiceName(java.lang.String serviceName)


// Returns the HTTP status code that was returned with this service exception.
int 	getStatusCode()

// Sets the HTTP status code that was returned with this service exception.
void 	setStatusCode(int statusCode)

```



### New Client Runtime Exception Hierarchy 

One of the problems is that the `smithy-LANG` packages are supposed to be AWS agnostic. They generate code for a target language and set of protocols supported. As such we really shouldn't introduce things like `AmazonException` into such a package. That is the point of the higher level codegen package to decorate and specialize codegen for AWS specific behaviors.


Given that, generated errors (from smithy-kotlin) will all inherit from a common exception hierarchy independent of AWS/Amazon.


```
RuntimeException
    SdkBaseException
        ClientException
            ServiceException
```


The actual `aws-sdk-kotlin` Smithy code generator should customize these error types so that the exceptions thrown match what's existing (e.g. AmazonClientException instead of ClientException).


The following exceptions would be defined in whatever the client runtime lib is that the generated smithy-kotlin code depends on. Everything in this hierarchy is independent of AWS specifics.


```kotlin

package software.amazon.smithy.kotlin.core


/**
 * Base exception class for all exceptions thrown by the SDK. Exception may be a client side exception or a service exception
 */
open class SdkBaseException: RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}

/**
 * Base exception class for any errors that occur while attempting to use an SDK client to make (Smithy) service calls.
 */
open class ClientException: SdkBaseException {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    open val isRetryable: Boolean = true
}

/**
 * ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this
 * type indicates that the caller's request was successfully transmitted to the service and the service sent back an
 * error response.
 */
open class ServiceException: ClientException {

    /**
     * Indicates who (if known) is at fault for this exception.
     */
    enum class ErrorType {
        Client,
        Server,
        Unknown
    }

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    /**
     * The name of the service that sent this error response
     */
    open val serviceName: String = ""

    /**
     * Indicates who is responsible for this exception (caller, service, or unknown)
     */
    open val errorType: ErrorType = ErrorType.Unknown

    /**
     * The human-readable error message provided by the service
     */
    open var errorMessage: String = ""

    // TODO - HTTP response/protocol response
    // What about non-HTTP protocols?
    open var httpResponse: HttpResponse? = null
}

```

**FIXME** Exposing `httpResponse` directly feels "wrong" here. Can we do it in a protocol agnostic way? Or is it actually ok since codegen is usually specific to a protocol?



The higher level AWS specific client runtime lib would build on top of those agnostic service exceptions. Error traits would be customized and processed using these exceptions instead of the generic ones.

```kotlin

package com.amazonaws

import software.amazon.smithy.kotlin.core.ServiceException

open class AmazonServiceException: ServiceException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    /**
     * The AWS request ID that uniquely identifies the service request the caller made
     */
    open var requestId: String = ""

    /**
     * The AWS error code represented by this exception
     */
    open var errorCode: String = ""

    /**
     * The HTTP Status code if known.
     * NOTE: This is here to match the legacy AmazonServiceException currently in use.
     */
    @Deprecated("Use the HttpResponse of ServiceException directly")
    val statusCode: Int
        // pull from the HTTPResponse of ServiceException if available otherwise default it
        get() = this.httpResponse?.statusCode ?: 0
}
```

**QUESTION** Can we actually make the exceptions backwards compatible or will the package rearrangement make that impossible? If it's not impossible what does the inheritance need to look like to make it work out? I believe it would be something like `RuntimeException <- SdkBaseException <- ClientException <- AmazonClientException <- ServiceException <- AmazonServiceException`. This all depends on what the split looks like for the runtime libraries. These needs more thought...


## Marshalling/Unmarshalling

**TODO** - See the `serialization.md` doc for some discussion.


## Project Structure

See the [example](https://github.com/aws-amplify/amplify-codegen/tree/smithy-kotlin/smithy-kotlin/design/example) in the staging repo.


## Pipeline (Request/Response Orchestration)

Other SDK's call this "middleware". The name isn't terribly important at the moment and we can change it, right now we are referring to it as a pipeline though. What is presented is a very early rough draft.

Ktor (which is our target HTTP client ATM) actually has a (generic) pipeline concept baked in. There are a few problems with using this out of the box though. One there doesn't seem to be a way to do per-request pipelines. The pipeline is attached at the [HttpClient](https://api.ktor.io/1.3.2/io.ktor.client/-http-client/index.html) level and is used for all requests sent by the client. This is useful for static things perhaps like setting a known `User-Agent` header but not for customizing individual requests and responses. We don't want to necessarily create a new HttpClient per request either since a client consumes resources. It is conceivable that we may want to share a client across multiple HTTP requests which is supported by Ktor. The second issue is we don't necessarily want to expose a library type we don't have control of. There are also concerns with the use of `suspend` in the function signature of [intercept](https://api.ktor.io/1.3.2/io.ktor.util.pipeline/-pipeline/intercept.html) which may not play well with iOS. 

[Ktor Generic Pipeline](https://github.com/ktorio/ktor/blob/d940855ac493aaadeeec4cd3c41b1c8de044311a/ktor-utils/common/src/io/ktor/util/pipeline/Pipeline.kt)
[Ktor HttpRequest Pipeline](https://github.com/ktorio/ktor/blob/master/ktor-client/ktor-client-core/common/src/io/ktor/client/request/HttpRequestPipeline.kt)
[Ktor HttpResponse Pipleine](https://github.com/ktorio/ktor/blob/3ce3906c2bfa3c86238d40e9b67006b2b020bdaf/ktor-client/ktor-client-core/common/src/io/ktor/client/statement/HttpResponsePipeline.kt)


That being said the design of the Ktor pipeline abstraction is _almost_ exactly what we want to define.  I think we can build heavily off this design with a few modifications such as making the interception points synchronous and tailored to our specific use case.


**TODO** We probably want to define the way phases proceed from one to the next as well as ways for a phase to fail and stop execution. When the intercept `block` runs to completion obviously it will move onto the next interceptor or phase. As example, Ktor allows a phase to modify the type being passed to the next phase with `proceedWith()`. 

```kotlin
class PipelinePhase(val name: String)

/**
 * Represents an execution pipeline for extensible computations in a defined sequence.
 */
open class Pipeline<TSubject: Any, TContext: Any> {
    constructor(vararg phases: PipelinePhase)

    fun addPhase(phase: PipelinePhase)
    fun insertPhaseAfter(target: PipelinePhase, phase: PipelinePhase)
    fun insertPhaseBefore(target: PipelinePhase, phase: PipelinePhase)

    fun intercept(phase: PipelinePhase, block: PipelineContext.(TSubject) -> Unit)

    fun execute(context: TContext, subject: TSubject): TSubject
}


class HttpRequestPipeline : Pipeline<Any, HttpRequestBuilder>(Before, Build, Transform, Finalize) {

    companion object Phases {

        /**
         * Execute any tasks before any starting transformations and building the request (e.g. input validation)
         */
        val Before = PipelinePhase("Before")

        /**
         * Modify the outgoing request properties (e.g. set headers)
         */
        val Build = PipelinePhase("Build")

        /**
         * Transform the input to a request body in the expected format (e.g. JSON)
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Perform final preparations before sending the message (e.g. SigV4 request signing)
         */
        val Finalize = PipelinePhase("Finalize")
    }
}


/**
 * Wrapper class containing a single HTTP request/response pair representing a single round-trip.
 */
data class HttpCall(val request: HttpRequest, val response: HttpResponse)

class HttpResponsePipeline : Pipeline<Any, HttpCall>(Receive, Transform, Finalize) {

    companion object Phases {
        /**
         * Execute any tasks before starting transformations on the response (e.g. inspect HTTP response headers)
         */
        val Receive = PipelinePhase("Receive")

        /**
         * Transform the response body to the expected format
         */
        val Transform = PipelinePhase("Transform")

        /**
         * Perform any final modifications to the response
         */
        val Finalize = PipelinePhase("Finalize")
    }
}


```


Possible usage:

```kotlin

class FooService {
    suspend fun getFoos(input: FooInput): FooOutput {
        var reqPipeline = HttpRequestPipeline()
        val respPipeline = HttpResponsePipeline()

        reqPipeline.intercept(HttpRequestPipeline.Before, input.validate)
        reqPipeline.intercept(HttpRequestPipeline.Build, setDefaults)
        reqPipeline.intercept(HttpRequestPipeline.Transform, input.serialize)
        respPipeline.intercept(HttpResponsePipeline.Transform, input.deserialize)

        return roundTrip<FooOutput>(input, reqPipeline, respPipeline)
        
    }
}

```

**TODO** This isn't really how we want to expose it since the client won't have access to customize the pipeline. This needs quite a bit more thought put into it but hopefully this gives a rough idea of where we might land.
**QUESTION** Should a pipeline be per request/response? The Go middleware indicates `A stack will use the ordered list of middleware to decorate a underlying handler. A handler could be something like an HTTP Client that round trips an API operation over HTTP.`. This is more typical of middleware I've seen (and how Ktor) works. As far as I can tell the Typescript SDK creates a new stack per command structure and inserts that types serialize/deserialize steps, etc.


