/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.shapes.*

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
        val clientErrorShape = model.expectShape(ShapeId.from("com.error.test#ValidationException"), StructureShape::class.java)
        val clientErrorGenerator = StructureGenerator(model, symbolProvider, errorWriter, clientErrorShape)
        clientErrorGenerator.render()
        clientErrorTestContents = errorWriter.toString()

        val serverErrorWriter = KotlinWriter("com.error.test")
        val serverErrorShape = model.expectShape(ShapeId.from("com.error.test#InternalServerException"), StructureShape::class.java)
        val serverErrorGenerator = StructureGenerator(model, symbolProvider, serverErrorWriter, serverErrorShape)
        serverErrorGenerator.render()
        serverErrorTestContents = serverErrorWriter.toString()
    }

    @Test
    fun `error generator extends correctly`() {
        val expectedClientClassDecl = """
class ValidationException private constructor(builder: BuilderImpl) : ServiceException() {
"""

        clientErrorTestContents.shouldContain(expectedClientClassDecl)

        val expectedServerClassDecl = """
class InternalServerException private constructor(builder: BuilderImpl) : ServiceException() {
"""

        serverErrorTestContents.shouldContain(expectedServerClassDecl)
    }

    @Test
    fun `error generator sets error type correctly`() {
        val expectedClientClassDecl = "override val errorType = ErrorType.Client"

        clientErrorTestContents.shouldContain(expectedClientClassDecl)

        val expectedServerClassDecl = "override val errorType = ErrorType.Server"
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
        val expectedConstr = """
    override val message: String = builder.message!!
"""

        serverErrorTestContents.shouldContain(expectedConstr)
        clientErrorTestContents.shouldNotContain(expectedConstr)
    }

    @Test
    fun `error generator renders isRetryable`() {
        val expectedConstr = """
        override val isRetryable = true
"""

        serverErrorTestContents.shouldContain(expectedConstr)
        clientErrorTestContents.shouldNotContain(expectedConstr)
    }
}
