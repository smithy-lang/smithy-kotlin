/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.SectionId
import software.amazon.smithy.kotlin.codegen.model.hasStreamingMember
import software.amazon.smithy.kotlin.codegen.model.operationSignature
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape

// FIXME - rename file and class to ServiceClientGenerator

/**
 * Renders just the service client interfaces. The actual implementation is handled by protocol generators, see
 * [software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingProtocolGenerator].
 */
class ServiceGenerator(private val ctx: RenderingContext<ServiceShape>) {
    /**
     * SectionId used when rendering the service interface companion object
     */
    object SectionServiceCompanionObject : SectionId {
        /**
         * Context key for the service symbol
         */
        const val ServiceSymbol = "ServiceSymbol"
    }

    /**
     * SectionId used when rendering the service configuration object
     */
    object SectionServiceConfig : SectionId {
        /**
         * The current rendering context for the service generator
         */
        const val RenderingContext = "RenderingContext"
    }

    init {
        require(ctx.shape is ServiceShape) { "ServiceShape is required for generating a service interface; was: ${ctx.shape}" }
    }

    private val service: ServiceShape =
        requireNotNull(ctx.shape) { "ServiceShape is required to render a service client" }
    private val serviceSymbol = ctx.symbolProvider.toSymbol(service)
    private val writer = ctx.writer

    fun render() {

        importExternalSymbols()

        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(ctx.model)

        writer.renderDocumentation(service)
        writer.renderAnnotations(service)
        writer.openBlock("public interface ${serviceSymbol.name} : SdkClient {")
            .call { overrideServiceName() }
            .call {
                // allow access to client's Config
                writer.dokka("${serviceSymbol.name}'s configuration")
                writer.write("public val config: Config")
            }
            .call {
                // allow integrations to add additional fields to companion object or configuration
                writer.write("")
                writer.declareSection(
                    SectionServiceCompanionObject,
                    context = mapOf(SectionServiceCompanionObject.ServiceSymbol to serviceSymbol)
                ) {
                    renderCompanionObject()
                }
                writer.write("")
                renderServiceConfig()
            }
            .call {
                operations.forEach { renderOperation(operationsIndex, it) }
            }
            .closeBlock("}")
            .write("")

        operations.forEach { renderOperationDslOverload(operationsIndex, it) }
    }

    private fun renderServiceConfig() {
        writer.declareSection(
            SectionServiceConfig,
            context = mapOf(SectionServiceConfig.RenderingContext to ctx)
        ) {
            ClientConfigGenerator(ctx).render()
        }
    }

    /**
     * Render the service interface companion object which is the main entry point for most consumers
     *
     * e.g.
     * ```
     * companion object {
     *     operator fun invoke(block: Config.Builder.() -> Unit = {}): LambdaClient {
     *         val config = Config.Builder().apply(block).build()
     *         return DefaultLambdaClient(config)
     *     }
     *
     *     operator fun invoke(config: Config): LambdaClient = DefaultLambdaClient(config)
     * }
     * ```
     */
    private fun renderCompanionObject() {
        writer.withBlock("public companion object {", "}") {
            val hasProtocolGenerator = ctx.protocolGenerator != null
            // If there is no ProtocolGenerator, do not codegen references to the non-existent default client.
            callIf(hasProtocolGenerator) {
                withBlock("public operator fun invoke(block: Config.Builder.() -> Unit = {}): ${serviceSymbol.name} {", "}") {
                    write("val config = Config.Builder().apply(block).build()")
                    write("return Default${serviceSymbol.name}(config)")
                }
                write("")
                write("public operator fun invoke(config: Config): ${serviceSymbol.name} = Default${serviceSymbol.name}(config)")
            }
        }
    }

    private fun importExternalSymbols() {
        // base client interface
        val sdkInterfaceSymbol = Symbol.builder()
            .name("SdkClient")
            .namespace(RUNTIME_ROOT_NS, ".")
            .addDependency(KotlinDependency.CORE)
            .build()

        writer.addImport(sdkInterfaceSymbol)

        // import all the models generated for use in input/output shapes
        writer.addImport("${ctx.settings.pkg.name}.model", "*")
    }

    private fun overrideServiceName() {
        writer.write("")
            .write("override val serviceName: String")
            .indent()
            .write("get() = #S", ctx.settings.sdkId)
            .dedent()
    }

    private fun renderOperation(opIndex: OperationIndex, op: OperationShape) {
        writer.write("")
        writer.renderDocumentation(op)
        writer.renderAnnotations(op)

        val signature = opIndex.operationSignature(ctx.model, ctx.symbolProvider, op, includeOptionalDefault = true)
        writer.write("public #L", signature)
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
                        "public suspend inline fun #T.#L(crossinline block: #T.Builder.() -> Unit): #T = #L(#T.Builder().apply(block).build())",
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
