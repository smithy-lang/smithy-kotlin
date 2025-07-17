/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering

import io.kotest.matchers.string.shouldNotContain
import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinCodegenPlugin
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.integration.SectionWriter
import software.amazon.smithy.kotlin.codegen.integration.SectionWriterBinding
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.expectShape
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
            class InternalServerException private constructor(builder: Builder) : TestException(builder.message) {
        """.trimIndent()

        serverErrorTestContents.shouldContainWithDiff(expectedServerClassDecl)
    }

    @Test
    fun `throwable message special cases`() {
        val model = """
        @httpError(500)
        @error("server")
        structure CapitalizedMessageMemberException {
            Message: String
        }
        
        @httpError(500)
        @error("server")
        structure NoMessageMemberException { }
        """
            .prependNamespaceAndService(version = "2.0")
            .toSmithyModel()

        val expectedCapMessageClassDecl = """
            class CapitalizedMessageMemberException private constructor(builder: Builder) : TestException(builder.message) {
        """.trimIndent()

        val expectedNoMessageClassDecl = """
            class NoMessageMemberException private constructor(builder: Builder) : TestException() {
        """.trimIndent()

        val provider: SymbolProvider = KotlinCodegenPlugin.createSymbolProvider(model)

        listOf(
            "com.test#CapitalizedMessageMemberException" to expectedCapMessageClassDecl,
            "com.test#NoMessageMemberException" to expectedNoMessageClassDecl,
        ).forEach { (shapeId, expected) ->

            val writer = KotlinWriter(TestModelDefault.NAMESPACE)
            val struct = model.expectShape<StructureShape>(shapeId)
            val renderingCtx = RenderingContext(writer, struct, model, provider, model.defaultSettings())
            StructureGenerator(renderingCtx).render()

            val generated = writer.toString()
            generated.shouldContainOnlyOnceWithDiff(expected)
        }
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
        e.message.shouldContainOnlyOnceWithDiff("message is a reserved name for exception types and cannot be used for any other property")
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
        fun itCanBeOverridden() {
            val model = "".prependNamespaceAndService().toSmithyModel()
            val exceptionBaseClassSymbol: Symbol = buildSymbol {
                name = "QuxException"
                namespace = "foo.bar"
            }
            val integration = object : KotlinIntegration {
                override val sectionWriters: List<SectionWriterBinding>
                    get() = listOf(SectionWriterBinding(ExceptionBaseClassGenerator.ExceptionBaseClassSection, exceptionSectionWriter))

                private val exceptionSectionWriter = SectionWriter { writer, _ ->
                    val ctx = writer.getContextValue(CodegenContext.Key)
                    ServiceExceptionBaseClassGenerator(exceptionBaseClassSymbol).render(ctx, writer)
                }
            }

            val ctx = model.newTestContext(integrations = listOf(integration))
            ctx.generationCtx.delegator.useFileWriter("Exception.kt", "") { writer ->
                ExceptionBaseClassGenerator.render(ctx.toCodegenContext(), writer)
            }
            ctx.generationCtx.delegator.flushWriters()

            val contents = ctx.manifest.getFileString("src/main/kotlin/Exception.kt").get()

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
