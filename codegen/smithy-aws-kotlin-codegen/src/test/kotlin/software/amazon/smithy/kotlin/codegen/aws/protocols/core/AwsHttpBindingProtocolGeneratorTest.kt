/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.aws.protocols.core

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.aws.protocols.json.AwsJsonHttpBindingResolver
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.*
import software.amazon.smithy.kotlin.codegen.test.defaultSettings
import software.amazon.smithy.kotlin.codegen.test.newTestContext
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.toSmithyModel
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.TimestampFormatTrait
import kotlin.test.Test

class AwsHttpBindingProtocolGeneratorTest {

    @Test
    fun itThrowsBaseServiceExceptionOnErrorParseFailure() {
        val model = """
            namespace com.test
            use aws.protocols#restJson1

            @restJson1
            service Example {
                version: "1.0.0",
                operations: [GetFoo]
            }

            operation GetFoo {
                errors: [FooError]
            }
            
            @error("server")
            structure FooError {
                payload: String
            }
        """.toSmithyModel()

        // This is the value that produces the name of the service base exception type
        val serviceSdkName = "SdkName"

        val testCtx = model.newTestContext(
            serviceName = "Example",
            settings = model.defaultSettings(sdkId = serviceSdkName),
        )
        val unit = TestableAwsHttpBindingProtocolGenerator()
        val op = model.expectShape<OperationShape>("com.test#GetFoo")

        val fn = unit.operationErrorHandler(testCtx.generationCtx, op)

        // use the symbol to ensure it's generated via GeneratedDependency
        testCtx.generationCtx.delegator.useFileWriter("GetFooOperationDeserializer.kt", "com.test.serde") {
            it.write("#T(context, response)", fn)
        }

        testCtx.generationCtx.delegator.finalize()
        testCtx.generationCtx.delegator.flushWriters()
        val actual = testCtx.manifest.expectFileString("src/main/kotlin/com/test/serde/GetFooOperationDeserializer.kt")
        val expected = """
            throw ${serviceSdkName}Exception("Failed to parse response as 'restJson1' error", ex).also {
        """.trimIndent()

        actual.shouldContainOnlyOnceWithDiff(expected)
    }

    @Test
    fun enumEventStreamHeader() {
        val model = """ 
            ${"$"}version: "2"
            
            namespace com.test
            
            use aws.protocols#restJson1

            @restJson1
            service Test {
                version: "1.0.0",
                operations: [TestStreamOp]
            }

            @http(method: "POST", uri: "/test-eventstream", code: 200)
            operation TestStreamOp {
                input: TestStreamInputOutput,
                output: TestStreamInputOutput,
            }
            
            structure TestStreamInputOutput {
                @httpPayload
                @required
                value: TestStream
            }
            
            @streaming
            union TestStream {
                MessageWithHeaders: MessageWithHeaders,
            }
            
            structure MessageWithHeaders {
                @eventHeader enum: Enum,
                @eventHeader intEnum: IntEnum,
            }
                        
            enum Enum {
                DIAMOND
                CLUB
                HEART
                SPADE
            }
            
            intEnum IntEnum {
                JACK = 1
                QUEEN = 2
                KING = 3
                ACE = 4
                JOKER = 5
            }
        """.toSmithyModel()
        val testCtx = model.newTestContext()
        val protocolGenerator = TestableAwsHttpBindingProtocolGenerator()
        val op = model.expectShape<OperationShape>("com.test#TestStreamOp")

        val serializer = protocolGenerator.eventStreamRequestHandler(testCtx.generationCtx, op)
        testCtx.generationCtx.delegator.useFileWriter("TestStreamOpOperationSerializer.kt", "com.test.serde") {
            it.write("#T(context, request)", serializer)
        }
        val deserializer = protocolGenerator.eventStreamResponseHandler(testCtx.generationCtx, op)
        testCtx.generationCtx.delegator.useFileWriter("TestStreamOpOperationDeserializer.kt", "com.test.serde") {
            it.write("#T(context, response)", deserializer)
        }

        testCtx.generationCtx.delegator.finalize()
        testCtx.generationCtx.delegator.flushWriters()

        val expectedEnumSerializer = """input.value.enum?.let { addHeader("enum", HeaderValue.String(it.value)) }"""
        val expectedIntEnumSerializer = """input.value.intEnum?.let { addHeader("intEnum", HeaderValue.Int32(it.value)) }"""
        val actualSerializer = testCtx.manifest.expectFileString("src/main/kotlin/com/test/serde/TestStreamOpOperationSerializer.kt")
        actualSerializer.shouldContainOnlyOnceWithDiff(expectedEnumSerializer)
        actualSerializer.shouldContainOnlyOnceWithDiff(expectedIntEnumSerializer)

        val expectedEnumDeserializer = """eb.enum = message.headers.find { it.name == "enum" }?.value?.expectEnumValue(Enum::fromValue)"""
        val expectedIntEnumDeserializer = """eb.intEnum = message.headers.find { it.name == "intEnum" }?.value?.expectIntEnumValue(IntEnum::fromValue)"""
        val actualDeserializer = testCtx.manifest.expectFileString("src/main/kotlin/com/test/serde/TestStreamOpOperationDeserializer.kt")
        actualDeserializer.shouldContainOnlyOnceWithDiff(expectedEnumDeserializer)
        actualDeserializer.shouldContainOnlyOnceWithDiff(expectedIntEnumDeserializer)
    }

    /**
     * A concrete implementation of AwsHttpBindingProtocolGenerator to exercise:
     *  renderThrowOperationError()
     *  getProtocolHttpBindingResolver()
     */
    class TestableAwsHttpBindingProtocolGenerator : HttpBindingProtocolGenerator() {
        override fun renderDeserializeErrorDetails(
            ctx: ProtocolGenerator.GenerationContext,
            op: OperationShape,
            writer: KotlinWriter,
        ) {
            // NOP
        }

        override val defaultTimestampFormat: TimestampFormatTrait.Format
            get() = error("Unneeded for test")

        override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
            AwsJsonHttpBindingResolver(model, serviceShape, "application/x-amz-json-1.0")

        override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator =
            object : StructuredDataParserGenerator {
                override fun operationDeserializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol {
                    error("Unneeded for test")
                }

                override fun errorDeserializer(
                    ctx: ProtocolGenerator.GenerationContext,
                    errorShape: StructureShape,
                    members: List<MemberShape>,
                ): Symbol {
                    error("Unneeded for test")
                }

                override fun payloadDeserializer(
                    ctx: ProtocolGenerator.GenerationContext,
                    shape: Shape,
                    members: Collection<MemberShape>?,
                ): Symbol {
                    error("Unneeded for test")
                }
            }

        override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator =
            object : StructuredDataSerializerGenerator {
                override fun operationSerializer(ctx: ProtocolGenerator.GenerationContext, op: OperationShape, members: List<MemberShape>): Symbol {
                    error("Unneeded for test")
                }

                override fun payloadSerializer(
                    ctx: ProtocolGenerator.GenerationContext,
                    shape: Shape,
                    members: Collection<MemberShape>?,
                ): Symbol {
                    error("Unneeded for test")
                }
            }

        override val protocol: ShapeId
            get() = error("Unneeded for test")
    }
}
