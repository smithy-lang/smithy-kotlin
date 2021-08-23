# Pagination Design

* **Type**: Design
* **Author(s)**: Aaron Todd

# Abstract

This document presents a design for how paginated operations will be generated.

Smithy services and operations can be marked with the [paginated trait](https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#paginated-trait) which 

> indicates that an operation intentionally limits the number of results returned in a single response an that multiple invocations might be necessary to retrieve all results


Pagination works off of a cursor convention where one of the operational input fields is an optional cursor the service uses to produce the next set of results not yet seen. The operation output has a field that marks the cursor position to be used to get the next set of results.

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
    1. Update 2/12/2021: The Smithy team has indicated this wouldn’t be a valid model evolution. The recommendation was still to provide a way to paginate over the raw responses as well as providing a flatMap like operation for the `items` if specified.


## API

The SDK runtime will provide a `Paginator` interface that represents the public API for a paginated operation.


```kotlin
/**
 * Controls automatic pagination of a set of results
 */
interface Paginator<out T> {
    /*
     * Flag indicating if more results are expected
     */
    val hasMorePages: Boolean

    /**
     * Returns the results for the next page or null when no more results are available
     */
    suspend fun next(): T?
}

// transforms implemented for paginators
// these are used internally for generating the `items` paginator but are also
// available for customers to use

inline fun<T, R> Paginator<T>.map(transform: (T) -> R): Paginator<R> = TODO("not-implemented")
inline fun<T, R> Paginator<Iterable<T>>.flatMap(transform: (T) -> Iterable<R>): Paginator<R> = TODO("not-implemented")
inline fun<T> Paginator<Iterable<T>>.flatten(): Paginator<T> = TODO("not-implemented")
```

Pagination by default will always generate a paginator _over the normal operation output type_. If `items` is specified then the generated paginator will be “specialized” to have an additional field that paginates only over the member targeted by `items`. An example of this is demonstrated below:


```kotlin
class ListFunctionsPaginator(
    private val client: LambdaClient,
    private val initialRequest: ListFunctionsRequest
): Paginator<ListFunctionsResponse>{
    private var cursor: String? = null
    private var isFirstPage: Boolean = true

    override val hasMorePages: Boolean
        get() = isFirstPage || (cursor?.isNotEmpty() ?: false)

    override suspend fun next(): ListFunctionsResponse? {
        if (!hasMorePages) return null

        val req = initialRequest.copy {
            this.marker = cursor
        }

        val result = client.listFunctions(req)
        isFirstPage = false
        cursor = result.nextMarker
        return result
    }
    
    // "functions" is the member targeted by the trait's `items` property
    val functions: Paginator<List<FunctionConfiguration>> = map(ListFunctionsResponse::functions)
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
    val pager = client.listFunctionsPaginated(req).functions // construction alt 2
    while(pager.hasMorePages) {
        val functions = pager.next()
        functions?.forEach {
            println(it)
        }
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

An alternative design would be to just expose paginators as flows rather than creating a new `Paginator` interface. This design hasn’t been fully explored but one issue is that this would expose the [Flow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/index.html) type which is not defined in the `stdlib` but rather in `kotlinx-coroutines-core`.


## Additional Considerations


### Flow Extension

For better coroutine support, the runtime (possibly in a separate extension package) could provide adapters for Paginators that expose paginated results as a [Flow](https://kotlinlang.org/docs/reference/coroutines/flow.html):


```kotlin
/*
 * Consume this [Paginator] as a [Flow]
 */
fun<T> Paginator<T>.asFlow(): Flow<T> = flow {
    while(hasMorePages) {
        val result = next(maxPageSize)
        if (result != null) {
            emit(result)
        }else {
            break
        }
    }
}
```

An example of consuming a paginator using flows:


```kotlin
suspend fun flowExample(client: LambdaClient) {
    val req = ListFunctionsRequest{}
    val functions = client.listFunctionsPaginated(req).functions.asFlow() 
    
    functions
        .flatMapConcat { it.asFlow() }   // not necessary - flattens the inner pages into a stream of T instead of List<T>
        .collect { 
            println(it)
        }
}
```


This extension should probably be provided by the runtime but it requires exposing a 3P type not defined in the stdlib ([Flow](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/-flow/index.html) is defined in `kotlinx-coroutines-core`). How we manage this from a dependency perspective would need answered first. 

# Revision history

* 8/23/2021 - Initial upload
* 2/05/2021 - Created

