package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.util.asSmithy
import software.amazon.smithy.kotlin.codegen.util.testModelChangeAgainstSource
import kotlin.test.Ignore

/**
 * These tests cover Smithy model changes that, by policy, are considered backward compatible against
 * sample code intended to verify that the changes indeed do not break customers.
 *
 * The current scope of these tests is compile-time only, however it should not be difficult to
 * execute any customer code from the generated class files.
 *
 * Generated SDKs are emitted via a parameter in tests, the test output emits the directory created to store the SDK sources.
 * Example: "Wrote generated SDK to /tmp/sdk-codegen-1606867139716"
 */
class ApiEvolutionTest {
    @Test
    fun `client calling operation with no input to operation with empty input compiles`() {
        val modelV1 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo { }
        """.asSmithy()

        val modelV2 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            structure PostFooRequest { }

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo {
                input: PostFooRequest
            }
        """.asSmithy()

        val customerCode = """
            import test.ExampleClient
            import test.model.PostFooRequest
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo(PostFooRequest{})
                    println(resp)
                }
            }
        """.trimIndent()

        testModelChangeAgainstSource(modelV1, modelV2, customerCode, Debug.emitSourcesToTemp).let { result ->
            assertTrue(result.compileSuccess, result.compileOutput)
        }
    }

    @Test
    fun `client calling operation with empty input to operation with input containing members compiles`() {
        val modelV1 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            structure PostFooRequest {}

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo {
                input: PostFooRequest
            }
        """.asSmithy()

        val modelV2 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            structure PostFooRequest {
                payload: String
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo {
                input: PostFooRequest
            }
        """.asSmithy()

        val customerCode = """
            import test.ExampleClient
            import test.model.PostFooRequest
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo(PostFooRequest {})
                    println(resp)
                }
            }
        """.trimIndent()

        testModelChangeAgainstSource(modelV1, modelV2, customerCode, Debug.emitSourcesToTemp).let { result ->
            assertTrue(result.compileSuccess, result.compileOutput)
        }
    }

    @Test
    fun `client calling operation with no output to operation with empty output compiles`() {
        val modelV1 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo { }
        """.asSmithy()

        val modelV2 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            structure PostFooResponse { }
            
            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo {
                output: PostFooResponse
            }
        """.asSmithy()

        val customerCode = """
            import test.ExampleClient
            import test.model.PostFooRequest
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo(PostFooRequest{})
                    println(resp)
                }
            }
        """.trimIndent()

        testModelChangeAgainstSource(modelV1, modelV2, customerCode, Debug.emitSourcesToTemp).let { result ->
            assertTrue(result.compileSuccess, result.compileOutput)
        }
    }

    @Test
    fun `client calling operation with empty output to operation with output containing members compiles`() {
        val modelV1 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            structure PostFooResponse { }

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo {
                output: PostFooResponse
            }
        """.asSmithy()

        val modelV2 = """
            namespace com.test

            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [
                    PostFoo,
                ]
            }

            structure PostFooResponse { 
                payload: String
            }

            @http(method: "POST", uri: "/foo-no-input")
            operation PostFoo {
                output: PostFooResponse
            }
        """.asSmithy()

        val customerCode = """
            import test.ExampleClient
            import test.model.PostFooRequest
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo(PostFooRequest{})
                    println(resp)
                }
            }
            
            
        """.trimIndent()

        testModelChangeAgainstSource(modelV1, modelV2, customerCode, Debug.emitSourcesToTemp).let { result ->
            assertTrue(result.compileSuccess, result.compileOutput)
        }
    }
}
