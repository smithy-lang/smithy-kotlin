# Pagination Design

* **Type**: Design
* **Author(s)**: Aaron Todd, Ken Gilmer

# Abstract

This document presents a design for how paginated operations are generated.

Smithy services and operations can be marked with
the [paginated trait](https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#paginated-trait)
which

> indicates that an operation intentionally limits the number of results returned in a single response and that multiple invocations might be necessary to retrieve all results

Pagination works off of a cursor convention where one of the operational input
fields is an optional cursor the service uses to produce the next set of results
not yet seen. The operation output has a field that marks the cursor position to
be used to get the next set of results in subsequent calls.

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
@http(method: "PUT", uri: "/functions", code: 200)
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

**NOTE**:
The [trait](https://awslabs.github.io/smithy/1.0/spec/core/behavior-traits.html#paginated-trait)
may apply at the service level to set default pagination. See the trait
definition and examples for the ways it can be used in a model.

## Considerations

1. **Discoverability** - pagination is mostly a convenience for common code
   customers are able to implement themselves. We want them to be able to
   discover and use the already implemented pagination logic easily so that they
   don’t end up writing it themselves unnecessarily.
2. **Forwards compatibility** - The use of `items` in the `paginated` trait is
   not required. ~~If a service team later updates the trait to target a
   specific member using `items` then previous pagination code should continue
   to work (which means you can’t really generate a paginator for only the
   member of `items` as adding `items` later would change the generated
   pagination code in a way that isn’t backwards compatible).~~
    1. Update 2/12/2021: The Smithy team has indicated this would not be a valid
       model evolution. The recommendation was still to provide a way to
       paginate over the raw responses as well as providing a flatMap like
       operation for the `items` if specified.

## API

The SDK codegen will render
a [Kotlin Flow](https://kotlinlang.org/docs/flow.html) that represents the
public API for the response of a paginated operation. Runtime library code is
not required for `Flow`-based paginators, as all functionality will be provided
by the Kotlin standard lib and the `kotlinx-coroutines-core` library.

Each operation that supports pagination would receive an extension function off the 
service client interface that returns a `Flow` over the normal operation output type.
The generated function name uses the operation name plus the suffix `Paginated`. 
This way the paginated and non-paginated operations should show up next to eachother 
in the IDE to promote discoverability.

An example of this is demonstrated below (codegen):

```kotlin
fun LambdaClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest): Flow<ListFunctionsResponse> =
    flow {
        var cursor: String? = null
        var isFirstPage: Boolean = true

        while (isFirstPage || (cursor?.isNotEmpty() == true)) {
            val req = initialRequest.copy {
                this.marker = cursor
            }
            val result = this@listFunctionsPaginated.listFunctions(req)
            isFirstPage = false
            cursor = result.nextMarker
            emit(result)
        }
    }
```

The shape targeted by `inputToken` is mapped to the `cursor` field. Each
iteration the `cursor` is updated with the output field targeted by
the `outputToken`.

### Pagination over Nested Item

If `items` is specified in the model for a given operation, then a `Flow`
transform function (extending the return type of it's parent) will be generated
that allows the nested item to be paginated over by producing another flow that
works from the base response flow. The targeted type will provide iteration to
the `item` element for the user automatically. The name of the generated
function comes from the name of the member.

Additionally, the `item` specified in the model may be deeply nested and involve
operations and types with many words. Generating a single function that combines
the operation, some word to indicate pagination, plus the nested item's name
often produces unreadable function signatures. Instead the member name targeted
by the `items` will be used. Thas has the advantage of matching the output structure
type property name.

Here is an example that follows from the previous example (codegen):

```kotlin
fun Flow<ListFunctionsResponse>.functions(): Flow<FunctionConfiguration> =
    transform() { response ->
        response.functions?.forEach {
            emit(it)
        }
    }
```


### Examples

#### Usage

An example of driving a paginator and processing response instances:

```kotlin
suspend fun rawPaginationExample(client: LambdaClient) {
    lambdaClient
        .listFunctionsPaginated(ListFunctionsRequest {})
        .collect { response ->
            response.functions?.forEach { functionConfiguration ->
                println(functionConfiguration.functionName)
            }
        }
}
```

#### Usage - Iterating over Modeled Item

Notice that this example is a superset of the previous. Simply by providing a
transform we are able to iterate over the nested element. As transforms can be
applied to any member of a type, users may extend the abstraction as needed
simply by providing their own flow transforms.


```kotlin
lambdaClient
    .listFunctionsPaginated(ListFunctionsRequest {})
    .functions()
    .collect { functionConfiguration ->
        println(functionConfiguration.functionName)
    }
```

#### Usage - Iterating over Maps

```kotlin
ApiGatewayClient.fromEnvironment().use { apiGatewayClient ->
    apiGatewayClient
        .getUsagePaginated(GetUsageRequest { })
        .items()
        .collect { entry -> // Map.Entry<String, List<List<Long>>>
            println("${entry.key}: ${entry.value}")
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

fun ListFunctionsRequest.paginate(client: LambdaClient): ListFunctionsPaginator =
    ListFunctionsPaginator(client, this)
```

In this alternative the extension would be on the operation input instead of the
service client.

Example usage:

```kotlin

...

val pager: SdkAsyncIterable<ListFunctionsResponse> =
    ListFunctionsRequest.paginate(client)

...
// usage after construction is same as before

```

This alternative was deemed less discoverable. Also having the extension on the
service client is more likely to provide IDE suggestions next to each other
like:

```
fun listFunctions(...)
fun listFunctionsPaginated(...)
```

### API/Codegen ALT 1 - Async Iterator Runtime
An alternative design is to implement an async iterator abstraction in our
runtime library. The primary advantage to this approach is complete control over
the API and no reliance on the `Flow` abstraction that is provided by JetBrains
but not supplied in the standard library.

The runtime abstraction would provide the following functions:
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
    suspend operator fun hasNext(): Boolean

    /**
     * Returns the results for the next page or null when no more results are available.
     * @throws NoSuchElementException if [hasMorePages()] is false
     */
    suspend operator fun next(): T
}

inline fun <T, R> SdkAsyncIterable<T>.map(transform: (T) -> R): SdkAsyncIterable<R> =
    TODO("not-implemented")
inline fun <T, R> SdkAsyncIterable<T>.mapNotNull(transform: (T) -> R?): SdkAsyncIterable<R> =
    TODO("not-implemented")
inline fun <T, R> SdkAsyncIterable<Iterable<T>>.flatMap(transform: (T) -> Iterable<R>): SdkAsyncIterable<R> =
    TODO("not-implemented")

fun <T> SdkAsyncIterable<Iterable<T>>.flatten(): SdkAsyncIterable<T> =
    TODO("not-implemented")
fun <T> SdkAsyncIterable<T?>.filterNotNull(): SdkAsyncIterable<T> =
    TODO("not-implemented")

suspend fun <T> SdkAsyncIterable<T>.toList(): List<T> = TODO("not-implemented")
suspend fun <T> SdkAsyncIterable<T>.toSet(): Set<T> = TODO("not-implemented")

/**
 * Terminal operator that collects the given iterable and calls [action] for each result
 */
inline fun <T> SdkAsyncIterable<T>.collect(crossinline action: suspend (T) -> Unit): Unit =
    TODO()
```

Then at codegen time, paginators would be generated like:

```kotlin
class ListFunctionsPaginator(
    private val client: LambdaClient,
    private val initialRequest: ListFunctionsRequest
) : SdkAsyncIterable<ListFunctionsResponse>,
    SdkAsyncIterator<ListFunctionsResponse> {
    private var cursor: String? = null
    private var isFirstPage: Boolean = true

    override operator fun iterator(): SdkAsyncIterator<ListFunctionsResponse> =
        this

    override suspend operator fun hasNext(): Boolean =
        isFirstPage || (cursor?.isNotEmpty() ?: false)

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
    val functions: SdkAsyncIterable<FunctionConfiguration> =
        mapNotNull(ListFunctionsResponse::functions).flatten()
}
```

Each operation that supports pagination would receive an extension function to
create a paginator. Using the model in the introduction would produce the
following:

```kotlin
// file: Paginators.kt
package aws.sdk.kotlin.services.lambda

import aws.sdk.kotlin.services.lambda.model.FunctionConfiguration
import aws.sdk.kotlin.services.lambda.model.ListFunctionsRequest

fun LambdaClient.listFunctionsPaginated(request: ListFunctionsRequest): ListFunctionsPaginator =
    ListFunctionsPaginator(this, request)
```

Example usage:

```kotlin
suspend fun rawPaginationExample(client: LambdaClient) {
    val req: ListFunctionsRequest = ListFunctionsRequest {}
    val pager: SdkAsyncIterable<FunctionConfiguration> =
        client.listFunctionsPaginated(req).functions
    for (fn in pager) {
        println(fn)
    }
}
```

While this approach is functionally complete, the introduction of a non-standard
async iterator that is not directly compatible with `Iterator`, `Iterable`, or
collection types, as well as support for Kotlin's async iterator
abstraction, `Flow`, presents usability issues to the customer. Per our tenet 1,
we work to provide customers with APIs that are simple and work in obvious,
familiar ways. Developers familiar with Kotlin's concurrency patterns in general
and flows in particular would likely find a `Flow`-based solution more natural
to work with. Additionally,
`Flow`s offer rich composition capabilities. Due to these reasons, the advantage
to using Flow is deemed to outweigh the risks of Flow API deprecation in the
future.

# Revision history

* 03/10/2022 - Fix function names
* 12/15/2021 - Adapted to favor Flow
* 8/23/2021 - Initial upload
* 2/05/2021 - Created

