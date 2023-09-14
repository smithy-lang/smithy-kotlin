/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.integration.SectionKey
import software.amazon.smithy.kotlin.codegen.lang.KotlinTypes
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.kotlin.codegen.model.operationSignature
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

/**
 * Renders just the service client interfaces. The actual implementation is handled by protocol generators, see
 * [software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator].
 */
class ServiceClientGenerator(private val ctx: RenderingContext<ServiceShape>) {
    object Sections {

        /**
         * [SectionId] used when rendering the service client builder
         */
        object ServiceBuilder : SectionId {
            /**
             * The current rendering context for the service generator
             */
            val RenderingContext: SectionKey<RenderingContext<ServiceShape>> = SectionKey("RenderingContext")
        }

        /**
         * [SectionId] used when rendering the service interface companion object
         */
        object CompanionObject : SectionId {
            /**
             * Context key for the service symbol
             */
            val ServiceSymbol: SectionKey<Symbol> = SectionKey("ServiceSymbol")

            /**
             * Context key for the SDK ID
             */
            val SdkId: SectionKey<String> = SectionKey("SdkId")
        }

        /**
         * [SectionId] used when rendering the service configuration object
         */
        object ServiceConfig : SectionId {
            /**
             * The current rendering context for the service generator
             */
            val RenderingContext: SectionKey<RenderingContext<ServiceShape>> = SectionKey("RenderingContext")
        }
    }

    init {
        require(ctx.shape is ServiceShape) { "ServiceShape is required for generating a service interface; was: ${ctx.shape}" }
    }

    private val service: ServiceShape =
        requireNotNull(ctx.shape) { "ServiceShape is required to render a service client" }
    private val serviceSymbol = ctx.symbolProvider.toSymbol(service)
    private val writer = ctx.writer
    private val visibility = ctx.settings.api.visibility

    fun render() {
        writer.write("\n\n")
        writer.write("$visibility const val ServiceId: String = #S", ctx.settings.sdkId)
        writer.write("$visibility const val SdkVersion: String = #S", ctx.settings.pkg.version)
        writer.write("\n\n")

        writer.putContext("service.name", ctx.settings.sdkId)

        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(ctx.model)

        writer.renderDocumentation(service)
        writer.renderAnnotations(service)
        writer.openBlock("$visibility interface ${serviceSymbol.name} : #T {", RuntimeTypes.SmithyClient.SdkClient)
            .call {
                // allow access to client's Config
                writer.dokka("${serviceSymbol.name}'s configuration")
                writer.write("public override val config: Config")
            }
            .call {
                // allow integrations to add additional fields to companion object or configuration
                writer.write("")
                writer.declareSection(
                    Sections.CompanionObject,
                    context = mapOf(
                        Sections.CompanionObject.ServiceSymbol to serviceSymbol,
                        Sections.CompanionObject.SdkId to ctx.settings.sdkId,
                    ),
                ) {
                    renderCompanionObject()
                }
                writer.write("")
                renderServiceBuilder()

                writer.write("")
                renderServiceConfig()
            }
            .call {
                operations.forEach { renderOperation(operationsIndex, it) }
            }
            .closeBlock("}")
            .write("")

        if (ctx.protocolGenerator != null) { // returns default impl, which only exists if there's a protocol generator
            renderWithConfig()
            writer.write("")
        }

        operations.forEach { renderOperationDslOverload(operationsIndex, it) }
    }

    private fun renderServiceConfig() {
        writer.declareSection(
            Sections.ServiceConfig,
            context = mapOf(Sections.ServiceConfig.RenderingContext to ctx),
        ) {
            ServiceClientConfigGenerator(service).render(ctx, ctx.writer)
        }
    }

    private fun renderServiceBuilder() {
        // don't generate a builder if there is no default client to instantiate
        if (ctx.protocolGenerator == null) return

        writer.declareSection(
            Sections.ServiceBuilder,
            context = mapOf(Sections.ServiceBuilder.RenderingContext to ctx),
        ) {
            writer.withBlock(
                "public class Builder internal constructor(): #T<Config, Config.Builder, #T>() {",
                "}",
                RuntimeTypes.SmithyClient.AbstractSdkClientBuilder,
                serviceSymbol,
            ) {
                write("override val config: Config.Builder = Config.Builder()")
                write("override fun newClient(config: Config): #T = Default${serviceSymbol.name}(config)", serviceSymbol)
            }
        }
    }

    /**
     * Render the service interface companion object which is the main entry point for most consumers
     *
     * e.g.
     * ```
     * companion object : SdkClientFactory<Config, Config.Builder, LambdaClient, Builder> {
     *     override fun builder: Builder = Builder()
     * }
     * ```
     */
    private fun renderCompanionObject() {
        // don't render a companion object which is used for building a service client unless we have a protocol generator
        if (ctx.protocolGenerator == null) return
        writer.withBlock(
            "public companion object : #T<Config, Config.Builder, #T, Builder> {",
            "}",
            RuntimeTypes.SmithyClient.SdkClientFactory,
            serviceSymbol,
        ) {
            write("@#T", KotlinTypes.Jvm.JvmStatic)
            write("override fun builder(): Builder = Builder()")
        }
    }

    private fun renderOperation(opIndex: OperationIndex, op: OperationShape) {
        writer.write("")
        writer.renderDocumentation(op)
        writer.renderAnnotations(op)

        val signature = opIndex.operationSignature(ctx.model, ctx.symbolProvider, op, includeOptionalDefault = true)
        // the signature returned by OperationIndex doesn't carry any import information with it, need to ensure
        // the input and output types are imported since the auto import machinery won't run
        listOf(
            ctx.symbolProvider.toSymbol(ctx.model.expectShape(op.inputShape)),
            ctx.symbolProvider.toSymbol(ctx.model.expectShape(op.outputShape)),
        ).forEach {
            writer.addImport(it)
        }
        writer.write("public #L", signature)
    }

    private fun renderWithConfig() {
        writer.dokka {
            write("Create a copy of the client with one or more configuration values overridden.")
            write("This method allows the caller to perform scoped config overrides for one or more client operations.")
            write("")
            write("Any resources created on your behalf will be shared between clients, and will only be closed when ALL clients using them are closed.")
            write("If you provide a resource (e.g. [HttpClientEngine]) to the SDK, you are responsible for managing the lifetime of that resource.")
        }
        writer.withBlock("$visibility fun #1T.withConfig(block: #1T.Config.Builder.() -> Unit): #1T {", "}", serviceSymbol) {
            write("val newConfig = config.toBuilder().apply(block).build()")
            write("return Default#L(newConfig)", serviceSymbol.name)
        }
    }

    private fun renderOperationDslOverload(opIndex: OperationIndex, op: OperationShape) {
        // Add DSL overload (if appropriate)
        opIndex.getInput(op).ifPresent { inputShape ->
            opIndex.getOutput(op).ifPresent { outputShape ->
                val hasOutputStream = outputShape.hasStreamingMember(ctx.model)

                if (!hasOutputStream) {
                    val inputSymbol = ctx.symbolProvider.toSymbol(inputShape)
                    val outputSymbol = ctx.symbolProvider.toSymbol(outputShape)
                    val operationName = op.defaultName()

                    writer.write("")
                    writer.renderDocumentation(op)
                    writer.renderAnnotations(op)
                    writer.write(
                        "$visibility suspend inline fun #T.#L(crossinline block: #T.Builder.() -> Unit): #T = #L(#T.Builder().apply(block).build())",
                        serviceSymbol,
                        operationName,
                        inputSymbol,
                        outputSymbol,
                        operationName,
                        inputSymbol,
                    )
                }
            }
        }
    }
}
