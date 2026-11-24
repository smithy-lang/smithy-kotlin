/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package aws.smithy.kotlin.codegen.aws.protocols

import aws.smithy.kotlin.codegen.test.lines
import aws.smithy.kotlin.codegen.test.newTestContext
import aws.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.shouldNotContainOnlyOnceWithDiff
import aws.smithy.kotlin.codegen.test.toSmithyModel
import io.kotest.matchers.string.shouldNotContain
import kotlin.test.Test

class AwsQueryTest {
    @Test
    fun testNonNestedIdempotencyToken() {
        val ctx = model.newTestContext("Example")

        val generator = AwsQuery()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.bar?.let { field(BAR_DESCRIPTOR, it) } ?: field(BAR_DESCRIPTOR, context.idempotencyTokenProvider.generateToken())
            }
        """.trimIndent()

        val actual = ctx
            .manifest
            .expectFileString("/src/main/kotlin/com/test/serde/GetBarUnNestedOperationSerializer.kt")
            .lines("    serializer.serializeStruct(OBJ_DESCRIPTOR) {", "    }")
            .trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun testNestedIdempotencyToken() {
        val ctx = model.newTestContext("Example")

        val generator = AwsQuery()
        generator.generateProtocolClient(ctx.generationCtx)

        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        val expected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.baz?.let { field(BAZ_DESCRIPTOR, it) }
            }
        """.trimIndent()

        val actual = ctx
            .manifest
            .expectFileString("/src/main/kotlin/com/test/serde/NestDocumentSerializer.kt")
            .lines("    serializer.serializeStruct(OBJ_DESCRIPTOR) {", "    }")
            .trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)

        val unexpected = """
            serializer.serializeStruct(OBJ_DESCRIPTOR) {
                input.baz?.let { field(BAZ_DESCRIPTOR, it) } ?: field(BAR_DESCRIPTOR, context.idempotencyTokenProvider.generateToken())
            }
        """.trimIndent()

        actual.shouldNotContainOnlyOnceWithDiff(unexpected)
    }

    @Test
    fun testOperationInputNestedAsMemberOmitsQueryLiterals() {
        val nestedInputModel = """
            ${"$"}version: "2"

            namespace com.test

            use aws.protocols#awsQuery
            use aws.api#service

            @awsQuery
            @service(sdkId: "Example")
            @xmlNamespace(uri: "http://foo.com")
            service Example {
                version: "1.0.0",
                operations: [PutFoo, PutFoos]
            }

            @http(method: "POST", uri: "/put-foo")
            operation PutFoo {
                input: PutFooRequest
            }

            @http(method: "POST", uri: "/put-foos")
            operation PutFoos {
                input: PutFoosRequest
            }

            structure PutFooRequest {
                v: String
            }

            structure PutFoosRequest {
                foo: PutFooRequest
            }
        """.toSmithyModel()
        val ctx = nestedInputModel.newTestContext("Example")

        AwsQuery().generateProtocolClient(ctx.generationCtx)
        ctx.generationCtx.delegator.finalize()
        ctx.generationCtx.delegator.flushWriters()

        // the operation's own request carries the literals...
        ctx.manifest
            .expectFileString("/src/main/kotlin/com/test/serde/PutFooOperationSerializer.kt")
            .shouldContainOnlyOnceWithDiff("""trait(QueryLiteral("Action", "PutFoo"))""")

        // ...but not when the same type is serialized as a nested member of another request
        val nested = ctx.manifest.expectFileString("/src/main/kotlin/com/test/serde/PutFooRequestDocumentSerializer.kt")
        nested.shouldNotContain("QueryLiteral")
        ctx.manifest
            .expectFileString("/src/main/kotlin/com/test/serde/PutFoosOperationSerializer.kt")
            .shouldContainOnlyOnceWithDiff("""trait(QueryLiteral("Action", "PutFoos"))""")
    }

    private val model = """
        ${"$"}version: "2"
    
        namespace com.test
        
        use aws.protocols#awsQuery
        use aws.api#service
    
        @awsQuery
        @service(sdkId: "Example")
        @xmlNamespace(uri: "http://foo.com")
        service Example {
            version: "1.0.0",
            operations: [GetBarUnNested, GetBarNested]
        }
    
        @http(method: "POST", uri: "/get-bar-un-nested")
        operation GetBarUnNested {
            input: BarUnNested
        }
        
        structure BarUnNested {
            @idempotencyToken
            bar: String
        }
        
        @http(method: "POST", uri: "/get-bar-nested")
        operation GetBarNested {
            input: BarNested
        }
        
        structure BarNested {
            bar: Nest
        }
        
        structure Nest {
            @idempotencyToken
            baz: String
        }
    """.toSmithyModel()
}
