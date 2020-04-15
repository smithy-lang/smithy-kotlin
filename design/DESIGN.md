# Kotlin Smithy SDK 

Reference the Smithy [Core Spec](https://awslabs.github.io/smithy/spec/core.html)

## Identifiers and Naming
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

## Simple Shapes


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
|document   | Unstable Represents an untyped JSON-like value that can take on one of the following types: null, boolean, string, byte, short, integer, long, float, double, an array of these types, or a map of these types where the key is string. | *Unsupported

**QUESTION**: We should support the `document` type but perhaps we can wait until it's stable?
**QUESTION**: The JVM has big number support but it is not MP (multi-platform) compatible. This is probably ok depending on how we structure the relationship between the generated SDK and the client runtime which will support MP.

### `document` Type

The `document` type is an  untyped JSON-like value that can take on the following types: null, boolean, string, byte, short, integer, long, float, double, an array of these types, or a map of these types where the key is string.


**TODO** Finish the design of document. This type is probably best represented as a Sum type (sealed class is closest we can get in Kotlin). See the [JSON type](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElement.kt) from the kotlinx.serialization lib for an example.


```kotlin

sealed class Document


object SmithyNull : Document()
data class SmithyInt(val value: Int): Document()
data class SmithyByte(val value: Byte): Document()
data class SmithyShort(val value: Short): Document()
data class SmithyLong(val value: Long): Document()
data class SmithyFloat(val value: Float): Document()
data class SmithyDouble(val value: Double): Document()
data class SmithyArray(val values: List<Document>): Document()
data class SmithyMap(val values: Map<String, Document>): Document()

fun Int.toDocument(): Document = SmithyInt(this)
fun Byte.toDocument(): Document = SmithyByte(this)
fun Short.toDocument(): Document = SmithyShort(this)
fun Long.toDocument(): Document = SmithyLong(this)
fun Float.toDocument(): Document = SmithyFloat(this)
fun Double.toDocument(): Document = SmithyDouble(this)

// TODO - define a Document builder DSL for easy creation of more complex document types. See [JSON builders](https://github.com/Kotlin/kotlinx.serialization/blob/master/runtime/commonMain/src/kotlinx/serialization/json/JsonElementBuilders.kt)

```



## Aggregate types


| Smithy Type | Kotlin Type
|-------------|-------------
| list        | List
| set         | Set
| map         | Map
| structure   | *Class
| union       | *sealed class


### Structure

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
    var quux: List<String>? = null)
}
```


The problem with this approach is readability since the number of constructor arguments could be potentially large. The form in alternative 1 can easily be initialized with the following code which makes this alternate less useful.

```kotlin
val f = Foo().apply {
    bar = "bar"
    baz = 12
    quux = listOf("foo", "bar", "quux")
}
```

### Union

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


## Service types


## Resource types


## Traits

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

The value will be stored as an additional property on the enum and passed in the constructor. This allows enums to be applied to other types in the future if Smithy evolves to allow it.


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
See [kotlinx.serialization annotations](https://github.com/Kotlin/kotlinx.serialization/blob/master/docs/examples.md#annotations) 

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

**TODO**


## Appendix

### Exceptions

The client runtime lib will expose the common exception types that all generated service/operation errors will be translated to (and inherit from).

#### Background: Current aws-sdk-android exception hierarchy

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



#### New Client Runtime Exception Hierarchy 

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

**TODO** Exposing `httpResponse` directly feels "wrong" here. Can we do it in a protocol agnostic way? Or is it actually ok since codegen is usually specific to a protocol?



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


### Marshalling/Unmarshalling

**TODO** - Need to define how types will be marshalled and interact with the client runtime package.
