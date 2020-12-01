package software.amazon.smithy.kotlin.codegen

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import software.amazon.smithy.kotlin.codegen.util.asSmithy
import software.amazon.smithy.kotlin.codegen.util.testModelChangeAgainstSource

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
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo()
                    println(resp)
                }
            }
        """.trimIndent()

        assertTrue(testModelChangeAgainstSource(modelV1, modelV2, customerCode, true).compileSuccess)
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

        assertTrue(testModelChangeAgainstSource(modelV1, modelV2, customerCode).compileSuccess)
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
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo()
                    println(resp)
                }
            }
        """.trimIndent()

        assertTrue(testModelChangeAgainstSource(modelV1, modelV2, customerCode, true).compileSuccess)
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

        // Create model w/ input
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
            import kotlinx.coroutines.runBlocking
            
            fun main() {
                val testClient = ExampleClient { }
                runBlocking {
                    val resp = testClient.postFoo()
                    println(resp)
                }
            }
        """.trimIndent()

        assertTrue(testModelChangeAgainstSource(modelV1, modelV2, customerCode, true).compileSuccess)
    }
}