/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator

/**
 * Renders the base class that all (modeled) exceptions inherit from.
 * Protocol generators are allowed to override this but they MUST inherit from the base `ServiceException`
 * with the expected constructors.
 */
object ExceptionBaseClassGenerator {
    fun render(ctx: CodegenContext, writer: KotlinWriter) {
        val baseException = ctx.protocolGenerator?.exceptionBaseClassSymbol ?: ProtocolGenerator.DefaultServiceExceptionSymbol
        writer.addImport(baseException)
        val serviceException = baseExceptionSymbol(ctx.settings)

        writer.dokka("Base class for all service related exceptions thrown by the ${ctx.settings.sdkId.clientName()} client")
        writer.withBlock(
            "open class #T : #T {", "}",
            serviceException,
            baseException
        ) {
            write("constructor() : super()")
            write("constructor(message: String?) : super(message)")
            write("constructor(message: String?, cause: Throwable?) : super(message, cause)")
            write("constructor(cause: Throwable?) : super(cause)")
        }
    }

    /**
     * Get the (generated) symbol that constitutes the base class exceptions will inherit from
     */
    fun baseExceptionSymbol(settings: KotlinSettings): Symbol = buildSymbol {
        val serviceName = settings.sdkId.clientName()
        name = "${serviceName}Exception"
        namespace = "${settings.pkg.name}.model"
        definitionFile = "$name.kt"
    }
}
