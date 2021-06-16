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
 * Renders just the service client interfaces. The actual implementation is handled by protocol generators
 */
class ServiceGenerator(private val ctx: RenderingContext<ServiceShape>) {
    /**
     * SectionId used when rendering the service interface companion object
     */
    object ServiceInterfaceCompanionObject : SectionId

    /**
     * SectionId used when rendering the service configuration object
     */
    object SectionServiceInterfaceConfig : SectionId

    init {
        require(ctx.shape is ServiceShape) { "ServiceShape is required for generating a service interface; was: ${ctx.shape}" }
    }

    private val service: ServiceShape = requireNotNull(ctx.shape) { "ServiceShape is required to render a service client" }
    private val serviceSymbol = ctx.symbolProvider.toSymbol(service)
    private val writer = ctx.writer

    fun render() {

        importExternalSymbols()

        val topDownIndex = TopDownIndex.of(ctx.model)
        val operations = topDownIndex.getContainedOperations(service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(ctx.model)

        writer.renderDocumentation(service)
        writer.renderAnnotations(service)
        writer.openBlock("interface ${serviceSymbol.name} : SdkClient {")
            .call { overrideServiceName() }
            .call {
                // allow integrations to add additional fields to companion object or configuration
                writer.write("")
                writer.declareSection(ServiceInterfaceCompanionObject) {
                    renderCompanionObject()
                }
                writer.write("")
                renderServiceConfig()
            }
            .call {
                operations.forEach { op ->
                    renderOperation(operationsIndex, op)
                }
            }
            .closeBlock("}")
            .write("")
    }

    private fun renderServiceConfig() {
        writer.declareSection(SectionServiceInterfaceConfig) {
            ClientConfigGenerator(ctx).render()
        }
    }

    /**
     * Render the service interface companion object which is the main entry point for most consumers
     *
     * e.g.
     * ```
     * companion object {
     *     fun build(block: Configuration.() -> Unit = {}): LambdaClient {
     *         val config = Configuration().apply(block)
     *         return DefaultLambdaClient(config)
     *     }
     * }
     * ```
     */
    private fun renderCompanionObject() {
        writer.openBlock("companion object {")
            .openBlock("operator fun invoke(block: Config.DslBuilder.() -> Unit = {}): ${serviceSymbol.name} {")
            .write("val config = Config.BuilderImpl().apply(block).build()")
            .write("return Default${serviceSymbol.name}(config)")
            .closeBlock("}")
            .closeBlock("}")
    }

    private fun importExternalSymbols() {
        // base client interface
        val sdkInterfaceSymbol = Symbol.builder()
            .name("SdkClient")
            .namespace(RUNTIME_ROOT_NS, ".")
            .addDependency(KotlinDependency.CLIENT_RT_CORE)
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
        writer.write(opIndex.operationSignature(ctx.model, ctx.symbolProvider, op))

        // Add DSL overload (if appropriate)
        opIndex.getInput(op).ifPresent { inputShape ->
            val outputShape = opIndex.getOutput(op)
            val hasOutputStream = outputShape.map { it.hasStreamingMember(ctx.model) }.orElse(false)

            if (!hasOutputStream) {
                val input = ctx.symbolProvider.toSymbol(inputShape).name
                val operationName = op.defaultName()

                writer.write("")
                writer.renderDocumentation(op)
                writer.renderAnnotations(op)
                val signature = "suspend fun $operationName(block: $input.DslBuilder.() -> Unit)"
                val impl = "$operationName($input.builder().apply(block).build())"
                writer.write("$signature = $impl")
            }
        }
    }
}
