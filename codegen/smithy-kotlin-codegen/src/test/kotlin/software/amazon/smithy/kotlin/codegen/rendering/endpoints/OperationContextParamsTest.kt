package software.amazon.smithy.kotlin.codegen.rendering.endpoints

import software.amazon.smithy.kotlin.codegen.test.*
import kotlin.test.Test

class OperationContextParamsTest {
    private fun codegen(paramType: String, jmesPath: String, input: String): String {
        val template =
            """
            namespace com.test
            
            use smithy.rules#endpointRuleSet
            use smithy.rules#operationContextParams
            
            @endpointRuleSet(
                version: "1.0",
                parameters: {
                    Foo: {
                        type: "$paramType",
                        documentation: "A foo",
                        required: false,
                    }
                }
                rules: []
            )
            service Test {
                version: "1.0.0",
                operations: [ TestOperation ],
            }
            
            @operationContextParams(
                Foo: {
                    path: "$jmesPath"
                }
            )
            operation TestOperation {
                input: TestOperationRequest
            }
        """

        val model = buildString {
            append(template)
            append(input)
        }.toSmithyModel()

        val testCtx = model.newTestContext()
        val writer = testCtx.newWriter()
        EndpointResolverAdapterGenerator(testCtx.generationCtx, writer).render()
        return writer.toString()
    }

    @Test
    fun testWildCardPath() {
        val input = """
            structure TestOperationRequest {
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
        """.trimIndent()

        val path = "Delete.Objects[*].Key"
        val pathResultType = "stringArray"

        val expected = """
            @Suppress("UNCHECKED_CAST")
            val input = request.context[HttpOperationContext.OperationInput] as TestOperationRequest
            val delete = input.delete
            val objects = delete?.objects
            val projection = objects?.flatMap {
                val key = it?.key
                listOfNotNull(key)
            }
            builder.foo = projection
        """.formatForTest("    ")

        codegen(pathResultType, path, input).shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testKeysFunctionPath() {
        val input = """
            structure TestOperationRequest {
                Object: Object
            }
            
            structure Object {
                Key1: String,
                Key2: String,
                Key3: String,
            }
        """.trimIndent()

        val path = "keys(Object)"
        val pathResultType = "stringArray"

        val expected = """
            @Suppress("UNCHECKED_CAST")
            val input = request.context[HttpOperationContext.OperationInput] as TestOperationRequest
            val object = input.object
            val keys = listOf("Key1", "Key2", "Key3")
            builder.foo = keys
        """.formatForTest("    ")

        codegen(pathResultType, path, input).shouldContainOnlyOnceWithDiff(expected)
    }
}
