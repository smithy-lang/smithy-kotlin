/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.kotlin.codegen.core.KotlinDependency
import software.amazon.smithy.kotlin.codegen.core.defaultName
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.protocoltests.traits.HttpResponseTestCase

open class HttpProtocolUnitTestErrorGenerator protected constructor(builder: Builder) :
    HttpProtocolUnitTestResponseGenerator(builder) {
    val error: Shape = builder.error ?: throw CodegenException("builder did not set an error shape")

    override val outputShape: Shape? = error

    override fun renderServiceCall() {
        writer.addImport(KotlinDependency.KOTLIN_TEST.namespace, "assertFailsWith")

        val inputParamName = operation.input.map { "input" }.orElse("")
        val isStreamingRequest = operation.input.map {
            val inputShape = model.expectShape(it)
            inputShape.asStructureShape().get().hasStreamingMember(model)
        }.orElse(false)

        // invoke the operation
        val opName = operation.defaultName()

        val errorShapeHasMembers = error.asStructureShape().get().members().isNotEmpty()
        val lhs = if (errorShapeHasMembers) "val actualResult = " else ""

        writer.openBlock("${lhs}assertFailsWith(#L::class) {", responseSymbol?.name)
            .call {
                if (isStreamingRequest) {
                    writer.openBlock("service.#L(#L){ result ->", opName, inputParamName)
                        .closeBlock("}")
                } else {
                    writer.write("service.#L(#L)", opName, inputParamName)
                }
            }
            .closeBlock("}")

        if (errorShapeHasMembers) {
            // "actualResult" in this case will be the exception class we expect to be thrown
            renderAssertions()
        }
    }

    open class Builder : HttpProtocolUnitTestResponseGenerator.Builder() {
        var error: Shape? = null

        /**
         * Set the error shape to generate the test for
         */
        fun error(shape: Shape) = apply { error = shape }

        override fun build(): HttpProtocolUnitTestGenerator<HttpResponseTestCase> {
            return HttpProtocolUnitTestErrorGenerator(this)
        }
    }
}
