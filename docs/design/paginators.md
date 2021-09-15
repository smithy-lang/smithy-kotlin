# Pagination Design

* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document presents a design for how paginated operations will be generated.

Smithy services and operations can be marked with the [paginated trait](https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#paginated-trait) which 

> indicates that an operation intentionally limits the number of results returned in a single response an that multiple invocations might be necessary to retrieve all results


Pagination works off of a cursor convention where one of the operational input fields is an optional cursor the service 
uses to produce the next set of results not yet seen. The operation output has a field that marks the cursor position 
to be used to get the next set of results in subsequent calls.

# Design

Below is an abbreviated example of the trait in use:

```
service Lambda {
    operations: [ListFunctions]
}

@paginated(
    inputToken: "Marker",
    outputToken: "NextMarker",
    pageSize: "MaxItems",
    items: "Functions"
)
@http(method: "PUT", uri: "/2015-03-31/functions", code: 200)
operation ListFunctions {
    input: ListFunctionsRequest,
    output: ListFunctionsResponse
}

structure ListFunctionsRequest {
    FunctionVersion: String,
    Marker: String,
    MasterRegion: String,
    MaxItems: Int
}

structure ListFunctionsResponse {
    Functions: FunctionConfigurationList,
    NextMarker: String
}

list FunctionConfigurationList {
    member: FunctionConfiguration
}

structure FunctionConfiguration { ... }
```

**NOTE**: The trait may be applied at the service level to set default pagination. See the trait definition and examples for the ways it can be used in a model.


## Considerations

1. **Discoverability** - pagination is mostly a convenience for common code customers are able to implement themselves. We want them to be able to discover and use the already implemented pagination logic easily so that they don’t end up writing it themselves unnecessarily. 
2. **Forwards compatibility** - The use of `items` in the `paginated` trait is not required. ~~If a service team later updates the trait to target a specific member using `items` then previous pagination code should continue to work (which means you can’t really generate a paginator for only the member of `items` as adding `items` later would change the generated pagination code in a way that isn’t backwards compatible).~~
    1. Update 2/12/2021: The Smithy team has indicated this would not be a valid model evolution. The recommendation was still to provide a way to paginate over the raw responses as well as providing a flatMap like operation for the `items` if specified.


## API

The SDK runtime will provide a `SdkAsyncIterable` interface that represents the public API for a paginated operation.


```kotlin
/**
 * An asynchronous data stream. This type is an asynchronous version of [Iterable].
 */
interface SdkAsyncIterable<out T> {
   operator fun iterator(): SdkAsyncIterator<T>
}

/**
 * An asynchronous [Iterator].
 */
interface SdkAsyncIterator<out T> {
   /**
    * Returns true if more results are expected
    */
   operator fun hasNext(): Boolean

   /**
    * Returns the results for the next page or null when no more results are available.
    * @throws NoSuchElementException if [hasMorePages()] is false
    */
   suspend operator fun next(): T
}

inline fun<T, R> SdkAsyncIterable<T>.map(transform: (T) -> R): SdkAsyncIterable<R> = TODO("not-implemented")
inline fun<T, R> SdkAsyncIterable<T>.mapNotNull(transform: (T) -> R?): SdkAsyncIterable<R> = TODO("not-implemented")
inline fun<T, R> SdkAsyncIterable<Iterable<T>>.flatMap(transform: (T) -> Iterable<R>): SdkAsyncIterable<R> = TODO("not-implemented")

fun<T> SdkAsyncIterable<Iterable<T>>.flatten(): SdkAsyncIterable<T> = TODO("not-implemented")
fun <T> SdkAsyncIterable<T?>.filterNotNull(): SdkAsyncIterable<T> = TODO("not-implemented")

suspend fun<T> SdkAsyncIterable<T>.toList(): List<T> = TODO("not-implemented")
suspend fun<T> SdkAsyncIterable<T>.toSet(): Set<T> = TODO("not-implemented")

/**
 * Terminal operator that collects the given iterable and calls [action] for each result
 */
inline fun <T> SdkAsyncIterable<T>.collect(crossinline action: suspend (T) -> Unit):Unit = TODO()
```

Pagination by default will always generate an `SdkAsyncIterable` over the normal operation output type. 
If `items` is specified then the generated paginator will be “specialized” to have an additional field that iterates 
only over the member targeted by `items`. The targeted type will be flattened for the user automatically.

An example of this is demonstrated below:


```kotlin
class ListFunctionsPaginator(
    private val client: LambdaClient,
    private val initialRequest: ListFunctionsRequest
): SdkAsyncIterable<ListFunctionsResponse>, SdkAsyncIterator<ListFunctionsResponse> {
   private var cursor: String? = null
   private var isFirstPage: Boolean = true

   override operator fun iterator(): SdkAsyncIterator<ListFunctionsResponse> = this

   override operator fun hasNext(): Boolean
           = isFirstPage || (cursor?.isNotEmpty() ?: false)

   override suspend operator fun next(): ListFunctionsResponse {
      if (!hasNext()) throw NoSuchElementException("no pages remaining")

      val req = initialRequest.copy {
         this.marker = cursor
      }

      val result = client.listFunctions(req)
      isFirstPage = false
      cursor = result.nextMarker
      return result
   }

   // "functions" is the member targeted by the trait's `items` property
   val functions: SdkAsyncIterable<FunctionConfiguration> = mapNotNull(ListFunctionsResponse::functions).flatten()
}
```

The shape targeted by `inputToken` is mapped to the `cursor` field. Each iteration the `cursor` is updated with the output field targeted by the `outputToken`.


### Creating a Paginator

Each operation that supports pagination would receive an extension function to create a paginator. Using the model in the introduction would produce the following:


```kotlin
// file: Paginators.kt
package aws.sdk.kotlin.services.lambda

import aws.sdk.kotlin.services.lambda.model.FunctionConfiguration
import aws.sdk.kotlin.services.lambda.model.ListFunctionsRequest

fun LambdaClient.listFunctionsPaginated(request: ListFunctionsRequest): ListFunctionsPaginator
    = ListFunctionsPaginator(this, request)
```



### Example Usage - Manual Pagination

An example of driving a paginator manually and processing results:

```kotlin
suspend fun rawPaginationExample(client: LambdaClient) {
    val req = ListFunctionsRequest{}
    val pager = client.listFunctionsPaginated(req).functions
    for (fn in pager) {
        println(fn)
    }
}
```


## Alternatives Considered


### Construction ALT 1 - Extension on the operation input

```kotlin
// file: Paginators.kt
package aws.sdk.kotlin.services.lambda

import aws.sdk.kotlin.services.lambda.model.FunctionConfiguration
import aws.sdk.kotlin.services.lambda.model.ListFunctionsRequest


fun ListFunctionsRequest.paginate(client: LambdaClient): ListFunctionsPaginator
    = ListFunctionsPaginator(client, this)

```

In this alternative the extension would be on the operation input instead of the service client.

Example usage:

```kotlin

...

val pager = ListFunctionsRequest.paginate(client)

...
// usage after construction is same as before

```

This alternative was deemed less discoverable. Also having the extension on the service client is more likely to provide IDE suggestions next to eachother like:

```
fun listFunctions(...)
fun listFunctionsPaginated(...)
```

### API ALT 1 - Expose Paginators as Flows

An alternative design would be to just expose paginators as flows rather than creating a new `Paginator` interface. 


```kotlin

package aws.sdk.kotlin.services.lambda

import aws.sdk.kotlin.services.lambda.model.FunctionConfiguration
import aws.sdk.kotlin.services.lambda.model.ListFunctionsRequest

fun LambdaClient.listFunctionsPaginated(request: ListFunctionsRequest): Flow<ListFunctionsResponse>
```


Example usage:
```kotlin
 val functions = client.listFunctionsPaginated(ListFunctionsRequest { maxItems = 10 } )
   .map { it.functions }
   .filterNotNull()
   .flatMapConcat{ it.asFlow() }
   .collect { fn -> println(fn) }
```

A few issues with this approach:

1. This would expose the [Flow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/index.html) type which is not defined in the `stdlib` but rather in `kotlinx-coroutines-core`.
2. `Flow` is not a type we control which could present forwards compatibility issues if the `paginated` trait changes in ways that we can't support.

Due to (2) it is not desirable to expose `Flow` as the type for pagination. See the flow extension section below.

## Additional Considerations

### Flow Extension

For better coroutine support, the runtime could provide adapters for Paginators that expose paginated results as a [Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html):


```kotlin
/*
 * Consume this [Paginator] as a [Flow]
 */
fun<T> SdkAsyncIterable<T>.asFlow(): Flow<T> = flow {
    val iter = iterator()
    while(iter.hasNext()) { 
        emit(iter.next())
    }
}
```

An example of consuming a paginator using flows:

```kotlin
suspend fun flowExample(client: LambdaClient) {
    val req = ListFunctionsRequest{}
    val functions = client.listFunctionsPaginated(req).functions.asFlow() 
    
    functions.collect { println(it) }
}
```


This extension would be provided by the runtime but it requires exposing a 3P type not defined in the stdlib ([Flow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/index.html) is defined in `kotlinx-coroutines-core`) as an `api()` dependency.

# Revision history

* 8/23/2021 - Initial upload
* 2/05/2021 - Created

