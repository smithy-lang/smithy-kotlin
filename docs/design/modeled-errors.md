# Kotlin Modeled Errors Design
* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document presents a design for how Smithy [modeled service errors](https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#error-trait) will be handled by the Kotlin code generator/SDK. 

See the additional references in the Appendix for overview of Smithy or prior design on how basic shapes will be mapped to Kotlin.

# Design

### Generic SDK Exceptions

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
      
    /**
     * Specifies whether or not an exception can be expected to succeed on a retry.
     */
    open val isRetryable: Boolean = true  
}  
  
/**  
* ServiceException - Base exception class for any error response returned by a service. Receiving an exception of this  
* type indicates that the caller's request was successfully transmitted to the service and the service sent back an  
* error response.  
*/  
open class ServiceException : ClientException {  
  
    /**  
    * Indicates who (if known) is at fault for this exception.  
    */  
    enum class ErrorType {  
        /**
         * Indicates the client is responsible for the error
         */
        Client,  

        /**
         * Indicates the server is responsible for the error
         */
        Server,  

        /**
         * The origin of the error is unknown
         */
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
     * The protocol response if available
     */
    open var protocolResponse: ProtocolResponse? = null  
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
class InvalidParameterValueException: ServiceException {
    constructor() : super()
    constructor(message: String?) : super(message)

    override val errorType: ErrorType = ErrorType.Client
    override val isRetryable: Boolean = false
    override val serviceName: String = "FooService"
    var type: String? = null
}

class ProxyTimedOut: ServiceException {
    constructor() : super()
    constructor(message: String?) : super(message)

    override val errorType: ErrorType = ErrorType.Server
    override val isRetryable: Boolean = true
    override val serviceName: String = "FooService"
}

```


The Smithy [error trait](https://awslabs.github.io/smithy/1.0/spec/core/type-refinement-traits.html#error-trait) indicates the following:


> The message member of an error structure is special-cased. It contains the human-readable message that describes the error. If the message member is not defined in the structure, code generated for the error may not provide an idiomatic way to access the error message (e.g., an exception message in Java).


 The `message` member (if present) is translated to the exception `message` field that is always present on all SDK exceptions.


**Protocol Response**

The current Android SDK and both the v1/v2 Java SDK's all provide access to pieces of the HTTP response (e.g. status code and headers) directly in the modeled service exceptions.

* [Android](https://aws-amplify.github.io/aws-sdk-android/docs/reference/index.html)
* [V2 Java](https://sdk.amazonaws.com/java/api/latest/index.html?software/amazon/awssdk/core/exception/SdkServiceException.html) (via AwsErrorDetails)

Even though the vast majority (if not all) of the AWS services can be accessed via HTTP this setup leaks protocol details directly into all service exceptions. Instead we can add extension functions to an opaque interface in each specific protocol package that give access to protocol specific fields. The instances of `ProtocolResponse` will differ per protocol.


```kotlin
// client-runtime common
interface ProtocolResponse
```



```kotlin
// http protocol package

/**
 * Get an HTTP header value by name. Returns the first header if multiple headers are set
 */
fun ProtocolResponse.header(name: String): String? {...}

/**
 * Get all HTTP header values associated with the given name.
 */
fun ProtocolResponse.getAllHeaders(name: String): List<String>? {...}

/**
 * Get the HTTP status code of the response
 */
fun ProtocolResponse.statusCode(): HttpStatusCode? {...}
```



### AWS Kotlin SDK Exceptions  

Modeled exceptions in the generated AWS Kotlin SDK will inherit from `AwsServiceException` which adds a few additional fields to the generic client runtime `ServiceException`.  
  

```kotlin
import generic-client-runtime-pkg.ServiceException  
  
open class AwsServiceException : ServiceException {  
  
    constructor() : super()  
      
    constructor(message: String?) : super(message)  
      
    constructor(message: String?, cause: Throwable?) : super(message, cause)  
      
    constructor(cause: Throwable?) : super(cause)  
      
    /**  
    * The requestId that was returned by the called service.  
    */  
    open var requestId: String = ""  
      
    /**  
    * Returns the error code associated with the response.  
    */  
    open var errorCode: String = ""  
      
    /**  
    * Returns the error message associated with the response.  
    */  
    open var errorMessage: String = ""  
}  
```



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

Error responses from services will be handled as AmazonServiceExceptions. This class is primarily for errors that occur when unable to get a response from a service, or when the client is unable to understand a response from a service. For example, if a caller tries to use a client to make a service call, but no network connection is present, an AmazonClientException will be thrown to indicate that the client wasn't able to successfully make the service call, and no information from the service is available.

Callers should typically deal with exceptions through AmazonServiceException, which represent error responses returned by services. AmazonServiceException has much more information available for callers to appropriately deal with different types of errors that can occur.


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

* 4/26/2021 - There were some minor changes to the way metadata is represented. The overall hierarchy here is still valid, see : [SDK Exception Hierarchy and Metadata Representation](sdk-exception-hierarchy-and-metadata-representation.md)
* 6/09/2021 - Initial upload
* 6/11/2020 - Created


