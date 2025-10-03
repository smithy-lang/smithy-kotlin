/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.CodegenException
import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace
import software.amazon.smithy.model.knowledge.TopDownIndex

/**
 * Renders the base class that all (modeled) exceptions inherit from.
 * Protocol generators are allowed to override this but they MUST inherit from the base `ServiceException`
 * with the expected constructors.
 *
 */
object ExceptionBaseClassGenerator {

    val DefaultServiceExceptionSymbol: Symbol = buildSymbol {
        name = "ServiceException"
        namespace(KotlinDependency.CORE)
    }

    /**
     * Defines a section in which code can be added to the body of the base exception type.
     */
    object ExceptionBaseClassSection : SectionId

    fun render(ctx: CodegenContext, writer: KotlinWriter) {
        writer.declareSection(ExceptionBaseClassSection) {
            ServiceExceptionBaseClassGenerator().render(ctx, writer)
        }
    }

    /**
     * Get the (generated) symbol that constitutes the base class exceptions will inherit from
     */
    fun baseExceptionSymbol(settings: KotlinSettings): Symbol = buildSymbol {
        val serviceName = clientName(settings.sdkId)
        name = "${serviceName}Exception"
        namespace = "${settings.pkg.name}.model"
        definitionFile = "$name.kt"
    }
}

/**
 * Re-usable component for generating the base exception that all generated error shapes will inherit from.
 * @param parent symbol to inherit from (defaults to [ExceptionBaseClassGenerator.DefaultServiceExceptionSymbol])
 */
open class ServiceExceptionBaseClassGenerator(
    private val parent: Symbol = ExceptionBaseClassGenerator.DefaultServiceExceptionSymbol,
) {
    fun render(ctx: CodegenContext, writer: KotlinWriter) {
        val serviceException = ExceptionBaseClassGenerator.baseExceptionSymbol(ctx.settings).also { checkForCollision(ctx, it) }

        val name = clientName(ctx.settings.sdkId)
        writer.dokka("Base class for all service related exceptions thrown by the $name client")
        writer.withBlock(
            "#L open class #T : #T {",
            "}",
            ctx.settings.api.visibility,
            serviceException,
            parent,
        ) {
            write("public constructor() : super()")
            write("public constructor(message: String?) : super(message)")
            write("public constructor(message: String?, cause: Throwable?) : super(message, cause)")
            write("public constructor(cause: Throwable?) : super(cause)")
            renderExtra(ctx, writer)
        }
    }

    /**
     * Hook for subclasses to render additional overrides or methods on the base exception
     */
    protected open fun renderExtra(ctx: CodegenContext, writer: KotlinWriter) { }

    // Compare generated base exception name with all error type names.  Throw exception if not unique.
    @Suppress("DEPRECATION")
    private fun checkForCollision(ctx: CodegenContext, exceptionSymbol: Symbol) {
        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(ctx.settings.service)

        operations.forEach { operationShape ->
            val errorNameToShapeIndex = operationShape.errors.associateBy { shapeId -> shapeId.name }
            if (errorNameToShapeIndex.containsKey(exceptionSymbol.name)) {
                throw CodegenException("Generated base error type '${exceptionSymbol.name}' collides with ${errorNameToShapeIndex[exceptionSymbol.name]}.")
            }
        }
    }
}
