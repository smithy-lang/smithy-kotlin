/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.build.MockManifest
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.CodegenContext
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.newTestContext
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model
import kotlin.test.Test

class PaginatorGeneratorTest {
    @Test
    fun testRenderPaginatorNoItem() {
        val testModelNoItem = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            structure FunctionConfiguration {
                FunctionName: String
            }
        """.toSmithyModel()
        val testContextNoItem = testModelNoItem.newTestContext("Lambda", "com.test")

        val codegenContextNoItem = object : CodegenContext {
            override val model: Model = testContextNoItem.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextNoItem.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextNoItem.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextNoItem.generator
            override val integrations: List<KotlinIntegration> = testContextNoItem.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextNoItem, testContextNoItem.generationCtx.delegator)

        testContextNoItem.generationCtx.delegator.flushWriters()
        val testManifest = testContextNoItem.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expected = """
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param initialRequest A [ListFunctionsRequest] to start pagination
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest = ListFunctionsRequest { }): Flow<ListFunctionsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFunctionsPaginated.listFunctions(req)
                        cursor = result.nextMarker
                        hasNextPage = cursor?.isNotEmpty() == true
                        emit(result)
                    }
                }
            
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param block A builder block used for DSL-style invocation of the operation
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(block: ListFunctionsRequest.Builder.() -> Unit): Flow<ListFunctionsResponse> =
                listFunctionsPaginated(ListFunctionsRequest.Builder().apply(block).build())
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testRenderPaginatorWithItem() {
        val testModelWithItems = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            structure FunctionConfiguration {
                FunctionName: String
            }
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param initialRequest A [ListFunctionsRequest] to start pagination
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest = ListFunctionsRequest { }): Flow<ListFunctionsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFunctionsPaginated.listFunctions(req)
                        cursor = result.nextMarker
                        hasNextPage = cursor?.isNotEmpty() == true
                        emit(result)
                    }
                }
            
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param block A builder block used for DSL-style invocation of the operation
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(block: ListFunctionsRequest.Builder.() -> Unit): Flow<ListFunctionsResponse> =
                listFunctionsPaginated(ListFunctionsRequest.Builder().apply(block).build())
            
            /**
             * This paginator transforms the flow returned by [listFunctionsPaginated]
             * to access the nested member [FunctionConfiguration]
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [FunctionConfiguration]
             */
            @JvmName("listFunctionsResponseFunctionConfiguration")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<FunctionConfiguration> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedCode)

        val expectedImports = """
            import com.test.model.FunctionConfiguration
            import com.test.model.ListFunctionsRequest
            import com.test.model.ListFunctionsResponse
            import kotlin.jvm.JvmName
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.flow.transform
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedImports)
    }

    @Test
    fun testRenderPaginatorWithItemRequiringFullName() {
        val testModelWithItems = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service FlowService {
                operations: [ListFlows]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Flows"
            )
            @readonly
            @http(method: "GET", uri: "/flows", code: 200)
            operation ListFlows {
                input: ListFlowsRequest,
                output: ListFlowsResponse
            }
            
            structure ListFlowsRequest {
                @httpQuery("FlowVersion")
                FlowVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFlowsResponse {
                Flows: FlowList,
                NextMarker: String
            }
            
            list FlowList {
                member: Flow
            }
            
            structure Flow {
                Name: String
            }
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("FlowService", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            /**
             * Paginate over [ListFlowsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param initialRequest A [ListFlowsRequest] to start pagination
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFlowsResponse]
             */
            public fun TestClient.listFlowsPaginated(initialRequest: ListFlowsRequest = ListFlowsRequest { }): kotlinx.coroutines.flow.Flow<ListFlowsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFlowsPaginated.listFlows(req)
                        cursor = result.nextMarker
                        hasNextPage = cursor?.isNotEmpty() == true
                        emit(result)
                    }
                }
            
            /**
             * Paginate over [ListFlowsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param block A builder block used for DSL-style invocation of the operation
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFlowsResponse]
             */
            public fun TestClient.listFlowsPaginated(block: ListFlowsRequest.Builder.() -> Unit): kotlinx.coroutines.flow.Flow<ListFlowsResponse> =
                listFlowsPaginated(ListFlowsRequest.Builder().apply(block).build())
            
            /**
             * This paginator transforms the flow returned by [listFlowsPaginated]
             * to access the nested member [Flow]
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [Flow]
             */
            @JvmName("listFlowsResponseFlow")
            public fun kotlinx.coroutines.flow.Flow<ListFlowsResponse>.flows(): kotlinx.coroutines.flow.Flow<Flow> =
                transform() { response ->
                    response.flows?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithSparseItem() {
        val testModelWithItems = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            @sparse
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            structure FunctionConfiguration {
                FunctionName: String
            }
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param initialRequest A [ListFunctionsRequest] to start pagination
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest = ListFunctionsRequest { }): Flow<ListFunctionsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFunctionsPaginated.listFunctions(req)
                        cursor = result.nextMarker
                        hasNextPage = cursor?.isNotEmpty() == true
                        emit(result)
                    }
                }
            
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param block A builder block used for DSL-style invocation of the operation
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(block: ListFunctionsRequest.Builder.() -> Unit): Flow<ListFunctionsResponse> =
                listFunctionsPaginated(ListFunctionsRequest.Builder().apply(block).build())
            
            /**
             * This paginator transforms the flow returned by [listFunctionsPaginated]
             * to access the nested member [FunctionConfiguration]
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [FunctionConfiguration]
             */
            @JvmName("listFunctionsResponseFunctionConfiguration")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<FunctionConfiguration?> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedCode)

        val expectedImports = """
            import com.test.model.FunctionConfiguration
            import com.test.model.ListFunctionsRequest
            import com.test.model.ListFunctionsResponse
            import kotlin.jvm.JvmName
            import kotlinx.coroutines.flow.Flow
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.flow.transform
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedImports)
    }

    @Test
    fun testRenderPaginatorWithTruncationMember() {
        val testModel = """
            namespace smithy.kotlin.traits
            
            use aws.protocols#restJson1
            
            @trait(selector: "*")
            string paginationEndBehavior
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems"
            )
            @paginationEndBehavior("TruncationMember:IsTruncated")
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer,
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                IsTruncated: Boolean,
                NextMarker: String
            }
            
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            structure FunctionConfiguration {
                FunctionName: String
            }
        """.toSmithyModel()
        val testContext = testModel.newTestContext("Lambda", "smithy.kotlin.traits")

        val codegenContext = object : CodegenContext {
            override val model = testContext.generationCtx.model
            override val symbolProvider = testContext.generationCtx.symbolProvider
            override val settings = testContext.generationCtx.settings
            override val protocolGenerator = testContext.generator
            override val integrations = testContext.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContext, testContext.generationCtx.delegator)

        testContext.generationCtx.delegator.flushWriters()
        val testManifest = testContext.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/smithy/kotlin/traits/paginators/Paginators.kt")

        val expectedCode = """
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest = ListFunctionsRequest { }): Flow<ListFunctionsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFunctionsPaginated.listFunctions(req)
                        cursor = result.nextMarker
                        hasNextPage = result.isTruncated == true
                        emit(result)
                    }
                }
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithIdenticalTokenTerminator() {
        val testModel = """
            namespace smithy.kotlin.traits
            
            use aws.protocols#restJson1
            
            @trait(selector: "*")
            string paginationEndBehavior
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems"
            )
            @paginationEndBehavior("IdenticalToken")
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer,
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            structure FunctionConfiguration {
                FunctionName: String
            }
        """.toSmithyModel()
        val testContext = testModel.newTestContext("Lambda", "smithy.kotlin.traits")

        val codegenContext = object : CodegenContext {
            override val model = testContext.generationCtx.model
            override val symbolProvider = testContext.generationCtx.symbolProvider
            override val settings = testContext.generationCtx.settings
            override val protocolGenerator = testContext.generator
            override val integrations = testContext.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContext, testContext.generationCtx.delegator)

        testContext.generationCtx.delegator.flushWriters()
        val testManifest = testContext.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/smithy/kotlin/traits/paginators/Paginators.kt")

        val expectedCode = """
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest = ListFunctionsRequest { }): Flow<ListFunctionsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFunctionsPaginated.listFunctions(req)
                        cursor = result.nextMarker
                        hasNextPage = cursor != null && cursor != req.marker
                        emit(result)
                    }
                }
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithRequiredInputMembers() {
        val testModelNoItem = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @required
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            structure FunctionConfiguration {
                FunctionName: String
            }
        """.toSmithyModel()
        val testContextNoItem = testModelNoItem.newTestContext("Lambda", "com.test")

        val codegenContextNoItem = object : CodegenContext {
            override val model: Model = testContextNoItem.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextNoItem.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextNoItem.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextNoItem.generator
            override val integrations: List<KotlinIntegration> = testContextNoItem.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextNoItem, testContextNoItem.generationCtx.delegator)

        testContextNoItem.generationCtx.delegator.flushWriters()
        val testManifest = testContextNoItem.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expected = """
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest): Flow<ListFunctionsResponse> =
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testRenderPaginatorFromResourceOperation() {
        val testModel = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                resources: [Function],
            }
            
            resource Function {
                identifiers: { id: String },
                list: ListFunctions,
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse,
            }
            
            structure ListFunctionsRequest {
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MaxItems")
                MaxItems: Integer,
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionsList,
                NextMarker: String,
            }
            
            list FunctionsList {
                member: FunctionSummary,
            }
            
            structure FunctionSummary {
                id: String,
                name: String
            }
        """.toSmithyModel()
        val testContext = testModel.newTestContext("Lambda", "com.test")

        val codegenContext = object : CodegenContext {
            override val model: Model = testContext.generationCtx.model
            override val symbolProvider: SymbolProvider = testContext.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContext.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContext.generator
            override val integrations: List<KotlinIntegration> = testContext.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContext, testContext.generationCtx.delegator)

        testContext.generationCtx.delegator.flushWriters()
        val testManifest = testContext.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expected = """
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param initialRequest A [ListFunctionsRequest] to start pagination
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(initialRequest: ListFunctionsRequest = ListFunctionsRequest { }): Flow<ListFunctionsResponse> =
                flow {
                    var cursor: kotlin.String? = initialRequest.marker
                    var hasNextPage: Boolean = true
            
                    while (hasNextPage) {
                        val req = initialRequest.copy {
                            this.marker = cursor
                        }
                        val result = this@listFunctionsPaginated.listFunctions(req)
                        cursor = result.nextMarker
                        hasNextPage = cursor?.isNotEmpty() == true
                        emit(result)
                    }
                }
            
            /**
             * Paginate over [ListFunctionsResponse] results.
             *
             * When this operation is called, a [kotlinx.coroutines.Flow] is created. Flows are lazy (cold) so no service
             * calls are made until the flow is collected. This also means there is no guarantee that the request is valid
             * until then. Once you start collecting the flow, the SDK will lazily load response pages by making service
             * calls until there are no pages left or the flow is cancelled. If there are errors in your request, you will
             * see the failures only after you start collection.
             * @param block A builder block used for DSL-style invocation of the operation
             * @return A [kotlinx.coroutines.flow.Flow] that can collect [ListFunctionsResponse]
             */
            public fun TestClient.listFunctionsPaginated(block: ListFunctionsRequest.Builder.() -> Unit): Flow<ListFunctionsResponse> =
                listFunctionsPaginated(ListFunctionsRequest.Builder().apply(block).build())
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testRenderPaginatorWithDocumentList() {
        val testModelWithItems = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            document FunctionConfiguration
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            @JvmName("listFunctionsResponseFunctionConfiguration")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<Document?> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()
        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithSparseDocumentList() {
        val testModelWithItems = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationList,
                NextMarker: String
            }
            
            @sparse
            list FunctionConfigurationList {
                member: FunctionConfiguration
            }
            
            document FunctionConfiguration
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            @JvmName("listFunctionsResponseFunctionConfiguration")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<Document?> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()
        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithDocumentMap() {
        val testModelWithItems = """
            namespace com.test
            
            use aws.protocols#restJson1
            
            service Lambda {
                operations: [ListFunctions]
            }
            
            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }
            
            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }
            
            structure ListFunctionsResponse {
                Functions: FunctionConfigurationMap,
                NextMarker: String
            }
            
            map FunctionConfigurationMap {
                key: String
                value: FunctionConfiguration
            }
            
            document FunctionConfiguration
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            @JvmName("listFunctionsResponseFunctionConfigurationMap")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<Map.Entry<String, Document?>> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()
        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithSparseDocumentMap() {
        val testModelWithItems = """
            namespace com.test

            use aws.protocols#restJson1

            service Lambda {
                operations: [ListFunctions]
            }

            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }

            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }

            structure ListFunctionsResponse {
                Functions: FunctionConfigurationMap,
                NextMarker: String
            }

            @sparse
            map FunctionConfigurationMap {
                key: String
                value: FunctionConfiguration
            }

            document FunctionConfiguration
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            @JvmName("listFunctionsResponseFunctionConfigurationMap")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<Map.Entry<String, Document?>> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()
        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithSparseStringMap() {
        val testModelWithItems = """
            namespace com.test

            use aws.protocols#restJson1

            service Lambda {
                operations: [ListFunctions]
            }

            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }

            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }

            structure ListFunctionsResponse {
                Functions: FunctionConfigurationMap,
                NextMarker: String
            }

            @sparse
            map FunctionConfigurationMap {
                key: String
                value: FunctionConfiguration
            }

            string FunctionConfiguration
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            @JvmName("listFunctionsResponseFunctionConfigurationMap")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<Map.Entry<String, String?>> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()
        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }

    @Test
    fun testRenderPaginatorWithStringMap() {
        val testModelWithItems = """
            namespace com.test

            use aws.protocols#restJson1

            service Lambda {
                operations: [ListFunctions]
            }

            @paginated(
                inputToken: "Marker",
                outputToken: "NextMarker",
                pageSize: "MaxItems",
                items: "Functions"
            )
            @readonly
            @http(method: "GET", uri: "/functions", code: 200)
            operation ListFunctions {
                input: ListFunctionsRequest,
                output: ListFunctionsResponse
            }

            structure ListFunctionsRequest {
                @httpQuery("FunctionVersion")
                FunctionVersion: String,
                @httpQuery("Marker")
                Marker: String,
                @httpQuery("MasterRegion")
                MasterRegion: String,
                @httpQuery("MaxItems")
                MaxItems: Integer
            }

            structure ListFunctionsResponse {
                Functions: FunctionConfigurationMap,
                NextMarker: String
            }

            map FunctionConfigurationMap {
                key: String
                value: FunctionConfiguration
            }

            string FunctionConfiguration
        """.toSmithyModel()
        val testContextWithItems = testModelWithItems.newTestContext("Lambda", "com.test")

        val codegenContextWithItems = object : CodegenContext {
            override val model: Model = testContextWithItems.generationCtx.model
            override val symbolProvider: SymbolProvider = testContextWithItems.generationCtx.symbolProvider
            override val settings: KotlinSettings = testContextWithItems.generationCtx.settings
            override val protocolGenerator: ProtocolGenerator = testContextWithItems.generator
            override val integrations: List<KotlinIntegration> = testContextWithItems.generationCtx.integrations
        }

        val unit = PaginatorGenerator()
        unit.writeAdditionalFiles(codegenContextWithItems, testContextWithItems.generationCtx.delegator)

        testContextWithItems.generationCtx.delegator.flushWriters()
        val testManifest = testContextWithItems.generationCtx.delegator.fileManifest as MockManifest
        val actual = testManifest.expectFileString("src/main/kotlin/com/test/paginators/Paginators.kt")

        val expectedCode = """
            @JvmName("listFunctionsResponseFunctionConfigurationMap")
            public fun Flow<ListFunctionsResponse>.functions(): Flow<Map.Entry<String, String>> =
                transform() { response ->
                    response.functions?.forEach {
                        emit(it)
                    }
                }
        """.trimIndent()
        actual.shouldContainOnlyOnceWithDiff(expectedCode)
    }
}
