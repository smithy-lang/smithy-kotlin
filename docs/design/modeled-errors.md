# Kotlin Modeled Errors Design
* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document presents a design for how Smithy [modeled service errors](https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#error-trait) will be handled by the Kotlin code generator/SDK. 

Generated exceptions have to deal with two sets of information:

1. The modeled exception data, these are fields found in the Smithy model and specific to the error
2. Un-modeled metadata, these are things that customers and or the SDK runtime care about but not found in the model and populated by the runtime. Examples include `requestId`, whether an error is retryable, the operation executed, etc.

This document weighs different designs for implementing Smithy modeled errors as Kotlin exceptions with particular attention paid to model conflicts and dealing with metadata.

See the additional references in the Appendix for overview of Smithy or prior design on how basic shapes will be mapped to Kotlin.

# Design

## SDK Exception Hierarchy

The following exceptions will be defined in the common client runtime. Modeled (Smithy) exceptions will inherit from `ServiceException`.  

```kotlin
  
/**  
* Base exception class for all exceptions thrown by the SDK. Exception may be a client side exception or a service exception  
*/  
open class SdkBaseException : RuntimeException {  
  
    constructor() : super()  
      
    constructor(message: String?) : super(message)  
      
    constructor(message: String?, cause: Throwable?) : super(message, cause)  
      
    constructor(cause: Throwable?) : super(cause)  
}  
  
/**  
* Base exception class for any errors that occur while attempting to use an SDK client to make (Smithy) service calls.  
*/  
open class ClientException : SdkBaseException {  
    constructor() : super()  
      
    constructor(message: String?) : super(message)  
      
    constructor(message: String?, cause: Throwable?) : super(message, cause)  
      
    constructor(cause: Throwable?) : super(cause)  
}  
  
/**  
* ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this  
* type indicates that the caller's request was successfully transmitted to the service and the service sent back an  
* error response.  
*/  
open class ServiceException : ClientException {  

    constructor() : super()  
      
    constructor(message: String?) : super(message)  
      
    constructor(message: String?, cause: Throwable?) : super(message, cause)  
      
    constructor(cause: Throwable?) : super(cause)  
}  
```


Given the following Smithy model for an error (assume the service is named `FooService`):
  

```
@error("client")
@httpError(400)
structure InvalidParameterValueException{
    Message: String
    Type: String
}


@error("server")
@retryable
@httpError(502)
structure ProxyTimedOut {
    Message: String
}
```


the following code would be generated:


```kotlin
class InvalidParameterValueException private constructor(builder: BuilderImpl): ServiceException {
    override val message: String? = builder.message
    var type: String? = builder.type

    ...
    // builder interfaces + impl
}

class ProxyTimedOut private constructor(builder: BuilderImpl): ServiceException {
    override val message: String? = builder.message


    ...
    // builder interfaces + impl
}

```

NOTE: The generated classes share the same builder pattern used as all other structure types which allows them to be serialized/deserialized the same way, see [Kotlin Smithy SDK](kotlin-smithy-sdk.md).

The Smithy [error trait](https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#error-trait) indicates the following:


> The message member of an error structure is special-cased. It contains the human-readable message that describes the error. If the message member is not defined in the structure, code generated for the error may not provide an idiomatic way to access the error message (e.g., an exception message in Java).


 The `message` member (if present) is translated to the exception `message` field that is always present on all SDK exceptions.

## AWS Kotlin SDK Exceptions  

Modeled exceptions in the generated AWS Kotlin SDK will inherit from `AwsServiceException`.
  

```kotlin
import aws.smithy.kotlin.runtime.ServiceException  
  
open class AwsServiceException : ServiceException {  
  
    constructor() : super()  
      
    constructor(message: String?) : super(message)  
      
    constructor(message: String?, cause: Throwable?) : super(message, cause)  
      
    constructor(cause: Throwable?) : super(cause)  
}  
```

## Metadata Representation

### Motivation

#### 1. Reduce the chance of conflicts between modeled and un-modeled fields

The way exceptions are generated currently has to be careful of conflicts between the modeled types and the un-modeled types.


As an example if un-modeled metadata fields are added to one of the base classes in the hierarchy it will increase the chance of conflicts:


```kotlin
/**
 * Base class for all modeled service exceptions
 */
public open class AwsServiceException : ServiceException {

    ...

    /**
     * The request ID that was returned by the called service
     */
    public open var requestId: String = ""

    /**
     * Returns the error code associated with the response
     */
    public open var errorCode: String = ""
}

```



These two fields `requestId` and `errorCode` both result in conflicts in a handful of models (and subsequently fails to compile) because the errors themselves have fields with matching names.


See: https://github.com/awslabs/smithy-kotlin/issues/110

As you can see this inherits from another type `ServiceException` which defines several other (un-modeled) fields further increasing the chance of a conflict. This also means that future modifications to the hierarchy could be difficult due to possible conflicts.

The exception hierarchy is defined [here](https://github.com/awslabs/smithy-kotlin/blob/v0.1.0-M0/client-runtime/client-rt-core/common/src/software/aws/clientrt/Exceptions.kt) and further extended [here](https://github.com/awslabs/aws-sdk-kotlin/blob/v0.1.0-M0/client-runtime/aws-client-rt/common/src/aws/sdk/kotlin/runtime/Exceptions.kt).


#### 2. A desire to remove mutability

The un-modeled fields are typically not known until runtime when a request or response is known. The original design chose to model these fields as mutable (`var`) properties such that they could be set when know. The overall SDK favors immutability whenever possible though and it's worth looking to see if we can do better here since the fields are generally only ever set once anyway and thus probably should be immutable from the customer's perspective.


#### 3. A desire to be open to extension

We don't know what the future holds or in what ways we may need to extend the hierarchy to e.g. special case a service or add new metadata. The ability to easily add new metadata in a backwards compatible way is a desirable property. Examples that come to mind are service specific extensions like S3 which has [two possible request id fields](https://aws.amazon.com/premiumsupport/knowledge-center/s3-request-id-values/)

#### PropertyBag + Extensions

The proposed design is to lift all the un-modeled (and even a few of the modeled) fields into a single extendable type (a property bag).

```kotlin
/**
 * Additional metadata about an error
 */
open class ErrorMetadata {
    @InternalApi
    val attributes: Attributes = Attributes()       // PropertyBag

    companion object {
        /**
         * Set if an error is retryable
         */
        val Retryable: AttributeKey<Boolean> = AttributeKey("Retryable")
    }

    val isRetryable: Boolean
        get() = attributes.getOrNull(Retryable) ?: false
}

/**
 * Base exception class for all exceptions thrown by the SDK. Exception may be a client side exception or a service exception
 */
open class SdkBaseException : RuntimeException {

    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)

    open val errorMetadata: ErrorMetadata = ErrorMetadata()
}

/**
 * Base exception class for any errors that occur while attempting to use an SDK client to make (Smithy) service calls.
 */
open class ClientException : SdkBaseException {
    constructor() : super()

    constructor(message: String?) : super(message)

    constructor(message: String?, cause: Throwable?) : super(message, cause)

    constructor(cause: Throwable?) : super(cause)
}

/**
 * Generic interface that any protocol (e.g. HTTP, MQTT, etc) can extend to provide additional access to
 * protocol specific details.
 */
interface ProtocolResponse

object EmptyProtocolResponse : ProtocolResponse

open class ServiceErrorMetadata : ErrorMetadata() {

    companion object {
        val ProtocolResponse: AttributeKey<ProtocolResponse> = AttributeKey("ProtocolResponse")

        val ErrorType: AttributeKey<ServiceException.ErrorType> = AttributeKey("ErrorType")

        val ServiceName: AttributeKey<String> = AttributeKey("ServiceName")
    }

    /**
     * The name of the service that sent this error response
     */
    val serviceName: String
        get() = attributes.getOrNull(ServiceName) ?: ""

    /**
     * Indicates who is responsible for this exception (caller, service, or unknown)
     */
    val errorType: ServiceException.ErrorType
        get() = attributes.getOrNull(ErrorType) ?: ServiceException.ErrorType.Unknown


    /**
     * The protocol response if available (this will differ depending on the underlying protocol e.g. HTTP, MQTT, etc)
     */
    val protocolResponse: ProtocolResponse
        get() = attributes.getOrNull(ProtocolResponse) ?: EmptyProtocolResponse

}

/**
 * ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this
 * type indicates that the caller's request was successfully transmitted to the service and the service sent back an
 * error response.
 */
open class ServiceException : SdkBaseException {

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

    override val errorMetadata: ServiceErrorMetadata = ServiceErrorMetadata()
}


open class AwsErrorMetadata : ServiceErrorMetadata() {
    companion object {
        val RequestId: AttributeKey<String> = AttributeKey("RequestId")
        val ErrorCode: AttributeKey<String> = AttributeKey("ErrorCode")
    }

    /**
     * The request ID that was returned by the called service
     */
    val requestId: String
        get() = attributes.getOrNull(RequestId) ?: ""

    /**
     * Returns the error code associated with the response
     */
    val errorCode: String
        get() = attributes.getOrNull(ErrorCode) ?: ""

}

/**
 * Base class for all modeled service exceptions
 */
public open class AwsServiceException : ServiceException {

    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    override val errorMetadata: AwsErrorMetadata = AwsErrorMetadata()
}

```


Example of an exception inheriting from this hierarchy (slightly simplified):

```kotlin
/**
 * Returned if the access point you are trying to create already exists
 */
class AccessPointAlreadyExistsException private constructor(builder: BuilderImpl) : AwsServiceException() {

    val errorCode: String? = builder.errorCode
    val errorMessage: String? = builder.errorMessage
    val accessPointId: String? = builder.accessPointId

    init {
        errorMetadata.attributes[ErrorMetadata.Retryable] = false
        errorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Client
    }

    interface DslBuilder {
        var errorCode: String?
        var errorMessage: String?
        var accessPointId: String?

        fun build(): AccessPointAlreadyExistsException
    }

    private class BuilderImpl : DslBuilder {
        override var errorCode: String? = ""
        override var errorMessage: String? = ""
        override var accessPointId: String? = ""

        constructor(): super()
        constructor(ex: AccessPointAlreadyExistsException) : super() {
            this.errorCode = ex.errorCode
            this.errorMessage = ex.errorMessage
            this.accessPointId = ex.accessPointId
        }

        override fun build(): AccessPointAlreadyExistsException = AccessPointAlreadyExistsException(this)
    }
}
```

##### Example usage

![Example Usage](resources/sdk-exception-metadata-usage-example.png)

##### Example extensions


```kotlin

class S3ErrorMetadata : AwsErrorMetadata() {
    companion object {
        val ExtendedRequestId: AttributeKey<String> = AttributeKey("S3:ExtendedRequestId")
    }

    val extendedRequestId: String
        get() = attributes.getOrNull(ExtendedRequestId) ?: ""
}

/**
 * Base class for all S3 errors
 */
class S3Exception : AwsServiceException {
    public constructor() : super()

    public constructor(message: String?) : super(message)

    public constructor(message: String?, cause: Throwable?) : super(message, cause)

    override val errorMetadata: S3ErrorMetadata = S3ErrorMetadata()
}

// ALTERNATIVELY defined as an extension property


val AwsErrorMetadata.extendedRequestId: String
    get() = attributes.getOrNull(S3ErrorAttributes.ExtendedRequestId) ?: ""
```


##### Advantages

* Gives the appearance of immutability while still allowing the fields to be set at runtime
* Easily extendable. The metadata type can be customized and generated per service and extension properties can be defined on it as needed.
* Significantly reduces the chance of conflicts since there is only one field, `errorMetadata` , to worry about (does not completely remove the chance of a conflict though).

##### Disadvantages

* Discoverability. Common properties can be defined in the base class (e.g. `AwsErrorMetadata.requestId`) but any extension properties past this may be slightly more difficult to discover. This could probably be mitigated somewhat by some careful choices around the package/subpackage that extensions are defined in such that it’s at least always consistent.


## Future Considerations

* Some SDKs expose fields like `requestId` on more than just the error types. If the Kotlin SDK chooses to do something similar we should reconcile and ensure that there is “one way” to get at a piece of data.

## Appendix

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

Error responses from services will be handled as `AmazonServiceException`. This class is primarily for errors that occur when unable to get a response from a service, or when the client is unable to understand a response from a service. For example, if a caller tries to use a client to make a service call, but no network connection is present, an `AmazonClientException` will be thrown to indicate that the client wasn't able to successfully make the service call, and no information from the service is available.

Callers should typically deal with exceptions through `AmazonServiceException`, which represent error responses returned by services. `AmazonServiceException` has much more information available for callers to appropriately deal with different types of errors that can occur.


The API currently looks like this:


```java
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

Extension of `AmazonClientException` that represents an error response returned by an Amazon web service. Receiving an exception of this type indicates that the caller's request was correctly transmitted to the service, but for some reason, the service was not able to process it, and returned an error response instead.

`AmazonServiceException` provides callers several pieces of information that can be used to obtain more information about the error and why it occurred. In particular, the `errorType` field can be used to determine if the caller's request was invalid, or the service encountered an error on the server side while processing it.


The API currently looks like this:

```java
enum ErrorType {
    Client, Service, Unknown
}


// Constructs a new AmazonServiceException with the specified message.
AmazonServiceException(java.lang.String errorMessage)

// Constructs a new AmazonServiceException with the specified message and exception indicating the root cause.
AmazonServiceException(java.lang.String errorMessage, java.lang.Exception cause)

// Returns the AWS error code represented by this exception.
String     getErrorCode()

// Sets the AWS error code represented by this exception.
void     setErrorCode(java.lang.String errorCode)

String     getErrorMessage() 

void     setErrorMessage(java.lang.String errorMessage) 

// Indicates who is responsible for this exception (caller, service, or unknown).
AmazonServiceException.ErrorType     getErrorType()

// Sets the type of error represented by this exception (sender, receiver, or unknown), indicating if this exception was the caller's fault, or the service's fault.
void     setErrorType(AmazonServiceException.ErrorType errorType)

String     getMessage() 

// Returns the AWS request ID that uniquely identifies the service request the caller made.
String     getRequestId()

// Sets the AWS requestId for this exception.
void     setRequestId(java.lang.String requestId)

// Returns the name of the service that sent this error response.
String     getServiceName()

// Sets the name of the service that sent this error response.
void     setServiceName(java.lang.String serviceName)


// Returns the HTTP status code that was returned with this service exception.
int     getStatusCode()

// Sets the HTTP status code that was returned with this service exception.
void     setStatusCode(int statusCode)

```



### Additional References

* [Kotlin Smithy SDK](kotlin-smithy-sdk.md)
* [Smithy Core Spec](https://awslabs.github.io/smithy/1.0/spec/core/shapes.html)


# Revision history

* 4/26/2021 - Refactor the metadata representation from properties directly on the base exception(s) to a property bag
* 6/09/2021 - Initial upload
* 6/11/2020 - Created


