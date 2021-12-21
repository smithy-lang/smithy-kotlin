package software.amazon.smithy.kotlin.codegen.rendering

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class PaginatorGeneratorTest {

    @Test
    fun testCanPaginateOnResponse() = runBlocking {
        val unit = ClientImpl()

        unit.paginateListFunctions(ListFunctionsRequest {}).collect { resp ->
            println(resp.functions)
        }
    }

    @Test
    fun testCanPaginateOnItem() = runBlocking {
        val unit = ClientImpl()

        unit
            .paginateListFunctions(ListFunctionsRequest {})
            .onFunctionConfiguration().collect { functionConfiguration ->
                println(functionConfiguration.functionName)
            }
    }
}

/**
 * Paginate over [ListFunctionsResponse]
 */
fun TestClient.paginateListFunctions(initialRequest: ListFunctionsRequest): Flow<ListFunctionsResponse> {
    return flow {
        var cursor: kotlin.String? = null
        var isFirstPage: Boolean = true

        while (isFirstPage || (cursor?.isNotEmpty() == true)) {
            val req = initialRequest.copy {
                this.marker = cursor
            }

            val result = this@paginateListFunctions.listFunctions(req)
            isFirstPage = false
            cursor = result.nextMarker
            emit(result)
        }
    }
}

/**
 * Paginate over [ListFunctionsResponse.functions]
 */
@JvmName("listFunctionsResponseFunctionConfiguration")
fun Flow<ListFunctionsResponse>.onFunctionConfiguration(): Flow<FunctionConfiguration> =
    transform() { response ->
        response.functions?.forEach {
            emit(it)
        }
    }

interface TestClient  {
    suspend fun listFunctions(input: ListFunctionsRequest): ListFunctionsResponse
}

class ClientImpl : TestClient {
    override suspend fun listFunctions(input: ListFunctionsRequest): ListFunctionsResponse {
        return ListFunctionsResponse.invoke {
            nextMarker = when {
                input.marker == null -> "."
                input.marker.length > 10 -> null
                else -> "${input.marker}."
            }

            val index = if (input.marker == null) 0 else input.marker.length

            functions = listOf(
                FunctionConfiguration { functionName = "Function ${index}.a" },
                FunctionConfiguration { functionName = "Function ${index}.b" },
                FunctionConfiguration { functionName = "Function ${index}.c" },
            )
        }
    }
}

class ListFunctionsRequest private constructor(builder: Builder) {
    val functionVersion: kotlin.String? = builder.functionVersion
    val marker: kotlin.String? = builder.marker
    val masterRegion: kotlin.String? = builder.masterRegion
    val maxItems: kotlin.Int? = builder.maxItems

    companion object {
        operator fun invoke(block: Builder.() -> kotlin.Unit): ListFunctionsRequest = Builder().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("ListFunctionsRequest(")
        append("functionVersion=$functionVersion,")
        append("marker=$marker,")
        append("masterRegion=$masterRegion,")
        append("maxItems=$maxItems)")
    }

    override fun hashCode(): kotlin.Int {
        var result = functionVersion?.hashCode() ?: 0
        result = 31 * result + (marker?.hashCode() ?: 0)
        result = 31 * result + (masterRegion?.hashCode() ?: 0)
        result = 31 * result + (maxItems ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ListFunctionsRequest

        if (functionVersion != other.functionVersion) return false
        if (marker != other.marker) return false
        if (masterRegion != other.masterRegion) return false
        if (maxItems != other.maxItems) return false

        return true
    }

    inline fun copy(block: Builder.() -> kotlin.Unit = {}): ListFunctionsRequest = Builder(this).apply(block).build()

    class Builder {
        var functionVersion: kotlin.String? = null
        var marker: kotlin.String? = null
        var masterRegion: kotlin.String? = null
        var maxItems: kotlin.Int? = null

        internal constructor()
        @PublishedApi
        internal constructor(x: ListFunctionsRequest) : this() {
            this.functionVersion = x.functionVersion
            this.marker = x.marker
            this.masterRegion = x.masterRegion
            this.maxItems = x.maxItems
        }

        @PublishedApi
        internal fun build(): ListFunctionsRequest = ListFunctionsRequest(this)
    }
}

class FunctionConfiguration private constructor(builder: Builder) {
    val functionName: kotlin.String? = builder.functionName

    companion object {
        operator fun invoke(block: Builder.() -> kotlin.Unit): FunctionConfiguration = Builder().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("FunctionConfiguration(")
        append("functionName=$functionName)")
    }

    override fun hashCode(): kotlin.Int {
        var result = functionName?.hashCode() ?: 0
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FunctionConfiguration

        if (functionName != other.functionName) return false

        return true
    }

    inline fun copy(block: Builder.() -> kotlin.Unit = {}): FunctionConfiguration = Builder(this).apply(block).build()

    class Builder {
        var functionName: kotlin.String? = null

        internal constructor()
        @PublishedApi
        internal constructor(x: FunctionConfiguration) : this() {
            this.functionName = x.functionName
        }

        @PublishedApi
        internal fun build(): FunctionConfiguration = FunctionConfiguration(this)
    }
}


class ListFunctionsResponse private constructor(builder: Builder) {
    val functions: List<FunctionConfiguration>? = builder.functions
    val nextMarker: kotlin.String? = builder.nextMarker

    companion object {
        operator fun invoke(block: Builder.() -> kotlin.Unit): ListFunctionsResponse = Builder().apply(block).build()
    }

    override fun toString(): kotlin.String = buildString {
        append("ListFunctionsResponse(")
        append("functions=$functions,")
        append("nextMarker=$nextMarker)")
    }

    override fun hashCode(): kotlin.Int {
        var result = functions?.hashCode() ?: 0
        result = 31 * result + (nextMarker?.hashCode() ?: 0)
        return result
    }

    override fun equals(other: kotlin.Any?): kotlin.Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ListFunctionsResponse

        if (functions != other.functions) return false
        if (nextMarker != other.nextMarker) return false

        return true
    }

    inline fun copy(block: Builder.() -> kotlin.Unit = {}): ListFunctionsResponse = Builder(this).apply(block).build()

    class Builder {
        var functions: List<FunctionConfiguration>? = null
        var nextMarker: kotlin.String? = null

        internal constructor()
        @PublishedApi
        internal constructor(x: ListFunctionsResponse) : this() {
            this.functions = x.functions
            this.nextMarker = x.nextMarker
        }

        @PublishedApi
        internal fun build(): ListFunctionsResponse = ListFunctionsResponse(this)
    }
}
