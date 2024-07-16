package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.test.*
import kotlin.test.*

class EndpointResolverAdapterTest {
    private val generatedClass: String

    init {
        val model =
            """
            namespace com.test
            
            use smithy.rules#endpointRuleSet
            use smithy.rules#operationContextParams
            
            @endpointRuleSet(
                version: "1.0",
                parameters: {
                    Foo: {
                        type: "stringArray",
                        documentation: "A foo",
                        required: false,
                    }
                }
                rules: []
            )
            service Test {
                version: "1.0.0",
                operations: [ DeleteObjects ],
            }
            
            @operationContextParams(
                Foo: {
                    path: "Delete.Objects[*].Key"
                }
            )
            operation DeleteObjects {
                input: DeleteObjectsRequest
            }
            
            structure DeleteObjectsRequest {
                Delete: Delete
            }
            
            structure Delete {
                Objects: ObjectIdentifierList
            }
            
            list ObjectIdentifierList {
                member: ObjectIdentifier
            }
            
            structure ObjectIdentifier {
                Key: String
            }
        """.toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        EndpointResolverAdapterGenerator(testCtx.generationCtx, writer).render()
        generatedClass = writer.toString()
    }

    @Test
    fun testClass() {
        val expected = """
            internal class EndpointResolverAdapter(
                private val config: TestClient.Config
            ): EndpointResolver {
        """.trimIndent()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testResolve() {
        val expected = """
            override suspend fun resolve(request: ResolveEndpointRequest): Endpoint {
                val params = resolveEndpointParams(config, request)
                val endpoint = config.endpointProvider.resolveEndpoint(params)
                return endpoint
            }
        """.formatForTest("    ")
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testResolveEndpointParams() {
        val expected = """
            internal fun resolveEndpointParams(config: TestClient.Config, request: ResolveEndpointRequest): TestEndpointParameters {
                return TestEndpointParameters {
                    val opName = request.context[SdkClientOption.OperationName]
                    opContextBindings[opName]?.invoke(this, request)
                }
            }
        """.trimIndent()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testOpContextBindingsMap() {
        val expected = """
            private val opContextBindings = mapOf<String, BindOperationContextParamsFn> (
                "DeleteObjects" to ::bindDeleteObjectsEndpointContext,
            )
        """.trimIndent()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testOpContextBindingsFunction() {
        val expected = """
            private fun bindDeleteObjectsEndpointContext(builder: TestEndpointParameters.Builder, request: ResolveEndpointRequest): Unit {
                val input = request.context[HttpOperationContext.OperationInput] as DeleteObjectsRequest
                val delete = input.delete
                val objects = delete?.objects
                val projection = objects?.flatMap {
                    val key = it?.key
                    listOfNotNull(key)
                }
                builder.foo = projection
            }
        """.trimIndent()
        generatedClass.shouldContainOnlyOnceWithDiff(expected)
    }
}
