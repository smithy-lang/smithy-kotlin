/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldNotContain
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.expectShape
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ApplicationProtocol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.kotlin.codegen.test.*
import software.amazon.smithy.model.shapes.*
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExceptionGeneratorTest {
    private val clientErrorTestContents: String
    private val serverErrorTestContents: String

    init {

        val model = """
        namespace com.error.test
        service Test { version: "1.0.0" }
        
        @enum([ { value: "Reason1", name: "REASON1" }, { value: "Reason2", name: "REASON2" } ])
        string ValidationExceptionReason
        
        structure ValidationExceptionField {}
        list ValidationExceptionFieldList {
            member: ValidationExceptionField
        }

        @httpError(400)
        @error("client")
        @documentation("Input fails to satisfy the constraints specified by the service")
        structure ValidationException {
            @documentation("Enumerated reason why the input was invalid")
            @required
            reason: ValidationExceptionReason,
            @documentation("List of specific input fields which were invalid")
            @required
            fieldList: ValidationExceptionFieldList
        }
        
        @httpError(500)
        @error("server")
        @retryable
        @documentation("Internal server error")
        structure InternalServerException {
            @required
            message: String
        }
        """.toSmithyModel()

        val symbolProvider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "com.error.test")
        val errorWriter = KotlinWriter("com.error.test")
        val clientErrorShape = model.expectShape<StructureShape>("com.error.test#ValidationException")
        val settings = model.defaultSettings(serviceName = "Test", packageName = "com.error.test")
        val clientErrorRenderingCtx = RenderingContext(errorWriter, clientErrorShape, model, symbolProvider, settings)
        StructureGenerator(clientErrorRenderingCtx).render()

        clientErrorTestContents = errorWriter.toString()

        val serverErrorWriter = KotlinWriter("com.error.test")
        val serverErrorShape = model.expectShape<StructureShape>("com.error.test#InternalServerException")
        val serverErrorRenderingCtx = RenderingContext(serverErrorWriter, serverErrorShape, model, symbolProvider, settings)
        StructureGenerator(serverErrorRenderingCtx).render()
        serverErrorTestContents = serverErrorWriter.toString()
    }

    @Test
    fun `error generator extends correctly`() {
        val expectedClientClassDecl = """
            class ValidationException private constructor(builder: Builder) : TestException() {
        """.trimIndent()

        clientErrorTestContents.shouldContainWithDiff(expectedClientClassDecl)

        val expectedServerClassDecl = """
            class InternalServerException private constructor(builder: Builder) : TestException() {
        """.trimIndent()

        serverErrorTestContents.shouldContainWithDiff(expectedServerClassDecl)
    }

    @Test
    fun `error generator sets error type correctly`() {
        val expectedClientClassDecl = "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Client"

        clientErrorTestContents.shouldContainWithDiff(expectedClientClassDecl)

        val expectedServerClassDecl = "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Server"
        serverErrorTestContents.shouldContainWithDiff(expectedServerClassDecl)
    }

    @Test
    fun `error generator syntactic sanity checks`() {
        // sanity check since we are testing fragments
        clientErrorTestContents.assertBalancedBracesAndParens()
        serverErrorTestContents.assertBalancedBracesAndParens()
    }

    @Test
    fun `error generator renders override with message member`() {
        val expected = """
    override val message: kotlin.String? = builder.message
"""

        serverErrorTestContents.shouldContainWithDiff(expected)
        clientErrorTestContents.shouldNotContain(expected)
    }

    @Test
    fun `error generator renders isRetryable`() {
        val expected = "sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true"
        serverErrorTestContents.shouldContainWithDiff(expected)
        clientErrorTestContents.shouldNotContain(expected)
    }

    @Test
    fun `it fails on conflicting property names`() {
        val model = """
        @httpError(500)
        @error("server")
        structure ConflictingException {
            SdkErrorMetadata: String
        }
        """.prependNamespaceAndService(namespace = "com.error.test").toSmithyModel()

        val symbolProvider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, rootNamespace = "com.error.test")
        val writer = KotlinWriter("com.error.test")
        val errorShape = model.expectShape<StructureShape>("com.error.test#ConflictingException")
        val renderingCtx = RenderingContext(writer, errorShape, model, symbolProvider, model.defaultSettings())
        val ex = assertFailsWith<CodegenException> {
            StructureGenerator(renderingCtx).render()
        }

        ex.message!!.shouldContainWithDiff("`sdkErrorMetadata` conflicts with property of same name inherited from SdkBaseException. Apply a rename customization/projection to fix.")
    }

    @Test
    fun `it fails if message property is of wrong type`() {
        val pkg = "com.error.test"
        val svc = "Test"
        val model = """
            namespace $pkg       
            service $svc { version: "1.0.0" }
            
            @httpError(500)
            @error("server")
            structure InternalServerException {
                message: Integer
            }
        """.toSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, pkg, svc)
        val writer = KotlinWriter(TestModelDefault.NAMESPACE)

        val struct = model.expectShape<StructureShape>("com.error.test#InternalServerException")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())

        val e = assertFailsWith<CodegenException> {
            StructureGenerator(renderingCtx).render()
        }
        e.message.shouldContainOnlyOnceWithDiff("Message is a reserved name for exception types and cannot be used for any other property")
    }

    class BaseExceptionGeneratorTest {

        @Test
        fun itGeneratesAnExceptionBaseClass() {
            val model = "".prependNamespaceAndService().toSmithyModel()
            val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
            val writer = KotlinWriter(TestModelDefault.NAMESPACE)

            val ctx = GenerationContext(model, provider, model.defaultSettings())
            ExceptionBaseClassGenerator.render(ctx, writer)
            val contents = writer.toString()

            val expected = """
                public open class TestException : ServiceException {
                    public constructor() : super()
                    public constructor(message: String?) : super(message)
                    public constructor(message: String?, cause: Throwable?) : super(message, cause)
                    public constructor(cause: Throwable?) : super(cause)
                }
            """.trimIndent()

            contents.shouldContainOnlyOnceWithDiff(expected)
        }

        @Test
        fun itExtendsProtocolGeneratorBaseClass() {
            val model = "".prependNamespaceAndService().toSmithyModel()
            val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
            val writer = KotlinWriter(TestModelDefault.NAMESPACE)

            val protocolGenerator = object : ProtocolGenerator {
                override val protocol: ShapeId
                    get() = error("not needed for test")

                override val applicationProtocol: ApplicationProtocol
                    get() = error("not needed for test")

                override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {}
                override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {}
                override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator {
                    error("not needed for test")
                }

                override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator {
                    error("not needed for test")
                }

                override val exceptionBaseClassSymbol: Symbol = buildSymbol {
                    name = "QuxException"
                    namespace = "foo.bar"
                }
            }

            val ctx = GenerationContext(model, provider, model.defaultSettings(), protocolGenerator)
            ExceptionBaseClassGenerator.render(ctx, writer)
            val contents = writer.toString()

            val expected = """
                public open class TestException : QuxException {
                    public constructor() : super()
                    public constructor(message: String?) : super(message)
                    public constructor(message: String?, cause: Throwable?) : super(message, cause)
                    public constructor(cause: Throwable?) : super(cause)
                }
            """.trimIndent()

            contents.shouldContainOnlyOnceWithDiff(expected)
            contents.shouldContainOnlyOnceWithDiff("import foo.bar.QuxException")
        }

        @Test
        fun itFailsIfBaseExceptionCollidesWithErrorType() {
            val model = """
                operation FooOperation {
                    errors: [TestException]
                }
                
                @error("client")
                structure TestException {
                    details: String
                }
            """.trimIndent().prependNamespaceAndService(operations = listOf("FooOperation")).toSmithyModel()
            val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)
            val writer = KotlinWriter(TestModelDefault.NAMESPACE)
            val ctx = GenerationContext(model, provider, model.defaultSettings())

            val e = assertFailsWith<CodegenException> {
                ExceptionBaseClassGenerator.render(ctx, writer)
            }
            e.message.shouldContainOnlyOnceWithDiff("Generated base error type 'TestException' collides with com.test#TestException.")
        }
    }
}
