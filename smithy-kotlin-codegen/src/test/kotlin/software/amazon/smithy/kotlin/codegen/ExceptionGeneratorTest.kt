/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.integration.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.test.asSmithyModel
import software.amazon.smithy.kotlin.codegen.test.defaultSettings
import software.amazon.smithy.kotlin.codegen.test.shouldContainOnlyOnceWithDiff
import software.amazon.smithy.kotlin.codegen.test.shouldSyntacticSanityCheck
import software.amazon.smithy.model.shapes.*
import kotlin.test.assertFailsWith

class ExceptionGeneratorTest {
    private val clientErrorTestContents: String
    private val serverErrorTestContents: String

    init {

        val model = """
        namespace com.error.test
        
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
        """.asSmithyModel()

        val symbolProvider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "error.test", "Test")
        val errorWriter = KotlinWriter("com.error.test")
        val clientErrorShape = model.expectShape<StructureShape>("com.error.test#ValidationException")
        val settings = model.defaultSettings(serviceName = "com.test.error#Foo")
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
class ValidationException private constructor(builder: BuilderImpl) : FooException() {
"""

        clientErrorTestContents.shouldContain(expectedClientClassDecl)

        val expectedServerClassDecl = """
class InternalServerException private constructor(builder: BuilderImpl) : FooException() {
"""

        serverErrorTestContents.shouldContain(expectedServerClassDecl)
    }

    @Test
    fun `error generator sets error type correctly`() {
        val expectedClientClassDecl = "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Client"

        clientErrorTestContents.shouldContain(expectedClientClassDecl)

        val expectedServerClassDecl = "sdkErrorMetadata.attributes[ServiceErrorMetadata.ErrorType] = ErrorType.Server"
        serverErrorTestContents.shouldContain(expectedServerClassDecl)
    }

    @Test
    fun `error generator syntactic sanity checks`() {
        // sanity check since we are testing fragments
        clientErrorTestContents.shouldSyntacticSanityCheck()
        serverErrorTestContents.shouldSyntacticSanityCheck()
    }

    @Test
    fun `error generator renders override with message member`() {
        val expected = """
    override val message: String? = builder.message
"""

        serverErrorTestContents.shouldContain(expected)
        clientErrorTestContents.shouldNotContain(expected)
    }

    @Test
    fun `error generator renders isRetryable`() {
        val expected = "sdkErrorMetadata.attributes[ErrorMetadata.Retryable] = true"
        serverErrorTestContents.shouldContain(expected)
        clientErrorTestContents.shouldNotContain(expected)
    }

    @Test
    fun `it fails on conflicting property names`() {
        val model = """
        namespace com.error.test
        
        @httpError(500)
        @error("server")
        structure ConflictingException {
            SdkErrorMetadata: String
        }
        """.asSmithyModel()

        val symbolProvider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "error.test", "Test")
        val writer = KotlinWriter("com.error.test")
        val errorShape = model.expectShape<StructureShape>("com.error.test#ConflictingException")
        val renderingCtx = RenderingContext(writer, errorShape, model, symbolProvider, model.defaultSettings())
        val ex = assertFailsWith<CodegenException> {
            StructureGenerator(renderingCtx).render()
        }

        ex.message!!.shouldContain("`sdkErrorMetadata` conflicts with property of same name inherited from SdkBaseException. Apply a rename customization/projection to fix.")
    }

    @Test
    fun `it fails if message property is of wrong type`() {
        val model = """
            namespace com.error.test
            
            @httpError(500)
            @error("server")
            structure InternalServerException {
                message: Integer
            }
        """.asSmithyModel()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
        val writer = KotlinWriter("com.test")

        val struct = model.expectShape<StructureShape>("com.error.test#InternalServerException")
        val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())

        val e = assertThrows<CodegenException> {
            StructureGenerator(renderingCtx).render()
        }
        e.message.shouldContainOnlyOnceWithDiff("Message is a reserved name for exception types and cannot be used for any other property")
    }

    class BaseExceptionGeneratorTest {

        @Test
        fun itGeneratesAnExceptionBaseClass() {
            val model = """
                namespace com.error.test
            """.asSmithyModel()
            val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
            val writer = KotlinWriter("com.test")

            val ctx = GenerationContext(model, provider, model.defaultSettings(serviceName = "com.error.test#Foo"))
            ExceptionBaseClassGenerator.render(ctx, writer)
            val contents = writer.toString()

            val expected = """
                open class FooException : ServiceException {
                    constructor() : super()
                    constructor(message: String?) : super(message)
                    constructor(message: String?, cause: Throwable?) : super(message, cause)
                    constructor(cause: Throwable?) : super(cause)
                }
            """.trimIndent()

            contents.shouldContainOnlyOnceWithDiff(expected)
        }

        @Test
        fun itExtendsProtocolGeneratorBaseClass() {
            val model = """
                namespace com.error.test
            """.asSmithyModel()
            val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model, "test", "Test")
            val writer = KotlinWriter("com.test")

            val protocolGenerator = object : ProtocolGenerator {
                override val protocol: ShapeId
                    get() = TODO("Not yet implemented")

                override val applicationProtocol: ApplicationProtocol
                    get() = TODO("Not yet implemented")

                override fun generateSerializers(ctx: ProtocolGenerator.GenerationContext) {}
                override fun generateDeserializers(ctx: ProtocolGenerator.GenerationContext) {}
                override fun generateProtocolUnitTests(ctx: ProtocolGenerator.GenerationContext) {}
                override fun generateProtocolClient(ctx: ProtocolGenerator.GenerationContext) {}

                override val exceptionBaseClassSymbol: Symbol = buildSymbol {
                    name = "QuxException"
                    namespace = "foo.bar"
                }
            }

            val ctx = GenerationContext(model, provider, model.defaultSettings(serviceName = "com.error.test#Foo"), protocolGenerator)
            ExceptionBaseClassGenerator.render(ctx, writer)
            val contents = writer.toString()

            val expected = """
                open class FooException : QuxException {
                    constructor() : super()
                    constructor(message: String?) : super(message)
                    constructor(message: String?, cause: Throwable?) : super(message, cause)
                    constructor(cause: Throwable?) : super(cause)
                }
            """.trimIndent()

            contents.shouldContainOnlyOnceWithDiff(expected)
            contents.shouldContainOnlyOnceWithDiff("import foo.bar.QuxException")
        }
    }
}
