/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package software.amazon.smithy.kotlin.codegen

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.knowledge.OperationIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.StructureShape
import software.amazon.smithy.model.traits.IdempotencyTokenTrait
import software.amazon.smithy.model.traits.StreamingTrait

/**
 * Section name used when rendering the service interface companion object
 */
const val SECTION_SERVICE_INTERFACE_COMPANION_OBJ = "service-interface-companion-obj"

/**
 * Section name used when rendering the service interface configuration object
 */
const val SECTION_SERVICE_INTERFACE_CONFIG = "service-interface-config"

const val SECTION_SERVICE_CONFIG_PARENT_TYPE = "serviceConfigParentType"

const val SECTION_SERVICE_CONFIG_PROPERTIES = "serviceConfigProperties"

const val SECTION_SERVICE_CONFIG_BUILDER_BODY = "serviceConfigBuilderBody"

const val SECTION_SERVICE_CONFIG_DSL_BUILDER_BODY = "serviceConfigDslBuilderBody"

const val SECTION_SERVICE_CONFIG_BUILDER_IMPL_PROPERTIES = "serviceConfigBuilderImplProperties"

const val SECTION_SERVICE_CONFIG_BUILDER_IMPL_CONSTRUCTOR = "serviceConfigBuilderImplConstructor"

const val SECTION_SERVICE_CONFIG_BUILDER_IMPL_BODY = "serviceConfigBuilderImplBody"

/**
 * Renders just the service interfaces. The actual implementation is handled by protocol generators
 */
class ServiceGenerator(
    private val model: Model,
    private val symbolProvider: SymbolProvider,
    private val writer: KotlinWriter,
    private val service: ServiceShape,
    private val rootNamespace: String,
    private val applicationProtocol: ApplicationProtocol
) {
    private val serviceSymbol = symbolProvider.toSymbol(service)

    fun render() {

        importExternalSymbols()

        val topDownIndex = TopDownIndex.of(model)
        val operations = topDownIndex.getContainedOperations(service).sortedBy { it.defaultName() }
        val operationsIndex = OperationIndex.of(model)

        writer.renderDocumentation(service)

        writer.openBlock("interface ${serviceSymbol.name} : SdkClient {")
            .call { overrideServiceName() }
            .call {
                // allow integrations to add additional fields to companion object or configuration
                writer.write("")
                writer.pushState(SECTION_SERVICE_INTERFACE_COMPANION_OBJ)
                renderCompanionObject()
                writer.popState()

                writer.write("")
                renderServiceConfigType()
            }
            .call {
                operations.forEach { op ->
                    renderOperation(operationsIndex, op)
                }
            }
            .closeBlock("}")
            .write("")
    }

    private fun renderServiceConfigType() {
        registerSections()

        writer.withState(SECTION_SERVICE_INTERFACE_CONFIG) {
            write("class Config private constructor(builder: BuilderImpl): \${L@$SECTION_SERVICE_CONFIG_PARENT_TYPE} {", "")

            withBlock("", "}") {
                withState(SECTION_SERVICE_CONFIG_PROPERTIES)
                blankLine()
                renderConfigCompanionObject()
                blankLine()
                renderConfigCopyFunction()
                blankLine()
                withBlock("interface Builder {", "}") {
                    write("fun build(): Config")
                    withState(SECTION_SERVICE_CONFIG_BUILDER_BODY)
                }
                blankLine()
                withBlock("interface DslBuilder {", "}") {
                    write("fun build(): Config")
                    withState(SECTION_SERVICE_CONFIG_DSL_BUILDER_BODY)
                }
                blankLine()
                withBlock("internal class BuilderImpl() : Builder, DslBuilder {", "}") {
                    withState(SECTION_SERVICE_CONFIG_BUILDER_IMPL_PROPERTIES)
                    blankLine()
                    withBlock("constructor(config: Config) : this() {", "}") {
                        withState(SECTION_SERVICE_CONFIG_BUILDER_IMPL_CONSTRUCTOR)
                    }
                    blankLine()
                    write("override fun build(): Config = Config(this)")
                    withState(SECTION_SERVICE_CONFIG_BUILDER_IMPL_BODY)
                }
            }
        }
    }

    private fun registerSections() {
        if (applicationProtocol.isHttpProtocol) {
            writer.onSection(SECTION_SERVICE_CONFIG_PARENT_TYPE) { text ->
                writer.addImport("HttpClientEngine", KotlinDependency.CLIENT_RT_HTTP, "${KotlinDependency.CLIENT_RT_HTTP.namespace}.engine")
                writer.addImport("HttpClientEngineConfig", KotlinDependency.CLIENT_RT_HTTP, "${KotlinDependency.CLIENT_RT_HTTP.namespace}.engine")
                writer.addImport("HttpClientConfig", KotlinDependency.CLIENT_RT_HTTP, "${KotlinDependency.CLIENT_RT_HTTP.namespace}.config")

                writer.appendWithDelimiter(text, "HttpClientConfig")
            }
        }

        if (service.hasIdempotentTokenMember(model)) {
            writer.onSection(SECTION_SERVICE_CONFIG_PARENT_TYPE) { text ->
                writer.addImport("IdempotencyTokenConfig", KotlinDependency.CLIENT_RT_CORE, "${KotlinDependency.CLIENT_RT_CORE.namespace}.config")
                writer.addImport("IdempotencyTokenProvider", KotlinDependency.CLIENT_RT_CORE, "${KotlinDependency.CLIENT_RT_CORE.namespace}.config")

                writer.appendWithDelimiter(text, "IdempotencyTokenConfig")
            }
        }

        writer.appendToSection(SECTION_SERVICE_CONFIG_PROPERTIES) {
            if (applicationProtocol.isHttpProtocol) {
                writer.write("override val httpClientEngine: HttpClientEngine? = builder.httpClientEngine")
                writer.write("override val httpClientEngineConfig: HttpClientEngineConfig? = builder.httpClientEngineConfig")
            }

            if (service.hasIdempotentTokenMember(model)) {
                writer.write("override val idempotencyTokenProvider: IdempotencyTokenProvider? = builder.idempotencyTokenProvider")
            }
        }

        writer.appendToSection(SECTION_SERVICE_CONFIG_BUILDER_BODY) {
            if (applicationProtocol.isHttpProtocol) {
                writer.write("fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder")
                writer.write("fun httpClientEngineConfig(httpClientEngineConfig: HttpClientEngineConfig): Builder")
            }

            if (service.hasIdempotentTokenMember(model)) {
                writer.write("fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder")
            }
        }

        writer.appendToSection(SECTION_SERVICE_CONFIG_DSL_BUILDER_BODY) {
            if (applicationProtocol.isHttpProtocol) {
                writer.write("var httpClientEngine: HttpClientEngine?")
                writer.write("var httpClientEngineConfig: HttpClientEngineConfig?")
            }

            if (service.hasIdempotentTokenMember(model)) {
                writer.write("var idempotencyTokenProvider: IdempotencyTokenProvider?")
            }
        }

        writer.appendToSection(SECTION_SERVICE_CONFIG_BUILDER_IMPL_PROPERTIES) {
            if (applicationProtocol.isHttpProtocol) {
                writer.write("override var httpClientEngine: HttpClientEngine? = null")
                writer.write("override var httpClientEngineConfig: HttpClientEngineConfig? = null")
            }

            if (service.hasIdempotentTokenMember(model)) {
                writer.write("override var idempotencyTokenProvider: IdempotencyTokenProvider? = null")
            }
        }

        writer.appendToSection(SECTION_SERVICE_CONFIG_BUILDER_IMPL_CONSTRUCTOR) {
            if (applicationProtocol.isHttpProtocol) {
                writer.write("this.httpClientEngine = config.httpClientEngine")
                writer.write("this.httpClientEngineConfig = config.httpClientEngineConfig")
            }

            if (service.hasIdempotentTokenMember(model)) {
                writer.write("this.idempotencyTokenProvider = config.idempotencyTokenProvider")
            }
        }

        writer.appendToSection(SECTION_SERVICE_CONFIG_BUILDER_IMPL_BODY) {
            if (applicationProtocol.isHttpProtocol) {
                writer.write("override fun httpClientEngine(httpClientEngine: HttpClientEngine): Builder = apply { this.httpClientEngine = httpClientEngine }")
                writer.write("override fun httpClientEngineConfig(httpClientEngineConfig: HttpClientEngineConfig): Builder = apply { this.httpClientEngineConfig = httpClientEngineConfig }")
            }

            if (service.hasIdempotentTokenMember(model)) {
                writer.write("override fun idempotencyTokenProvider(idempotencyTokenProvider: IdempotencyTokenProvider): Builder = apply { this.idempotencyTokenProvider = idempotencyTokenProvider }")
            }
        }
    }

    private fun renderConfigCopyFunction() {
        writer.write("fun copy(block: DslBuilder.() -> Unit = {}): Config = BuilderImpl(this).apply(block).build()")
    }

    private fun renderConfigCompanionObject() {
        writer.openBlock("companion object {")
        writer.write("@JvmStatic")
        writer.write("fun builder(): Builder = BuilderImpl()")
        writer.write("fun dslBuilder(): DslBuilder = BuilderImpl()")
        writer.write("operator fun invoke(block: DslBuilder.() -> Unit): Config = BuilderImpl().apply(block).build()")
        writer.closeBlock("}")
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
            .namespace(CLIENT_RT_ROOT_NS, ".")
            .addDependency(KotlinDependency.CLIENT_RT_CORE)
            .build()

        writer.addImport(sdkInterfaceSymbol)

        // import all the models generated for use in input/output shapes
        writer.addImport("$rootNamespace.model", "*")
    }

    private fun overrideServiceName() {
        writer.write("")
            .write("override val serviceName: String")
            .indent()
            .write("get() = \"\$L\"", service.id.name)
            .dedent()
    }

    private fun renderOperation(opIndex: OperationIndex, op: OperationShape) {
        writer.write("")
        writer.renderDocumentation(op)
        writer.write(opIndex.operationSignature(model, symbolProvider, op))
    }
}

fun StructureShape.hasStreamingMember(model: Model): Boolean =
    this.allMembers.values.any { model.getShape(it.target).get().hasTrait(StreamingTrait::class.java) }

// Returns true if any operation bound to the service contains an input member marked with the IdempotencyTokenTrait
fun ServiceShape.hasIdempotentTokenMember(model: Model) =
    this.operations.any { operationShapeId ->
        val operation = model.expectShape(operationShapeId) as OperationShape
        operation.input.isPresent &&
            model.expectShape(operation.input.get()).members().any { it.hasTrait(IdempotencyTokenTrait.ID.name) }
    }

/**
 * Return the formatted (Kotlin) function signature for the given operation
 */
fun OperationIndex.operationSignature(model: Model, symbolProvider: SymbolProvider, op: OperationShape): String {
    val inputShape = this.getInput(op)
    val outputShape = this.getOutput(op)
    val input = inputShape.map { symbolProvider.toSymbol(it).name }
    val output = outputShape.map { symbolProvider.toSymbol(it).name }

    val hasOutputStream = outputShape.map { it.hasStreamingMember(model) }.orElse(false)
    val inputParam = input.map { "input: $it" }.orElse("")
    val outputParam = output.map { ": $it" }.orElse("")

    val operationName = op.defaultName()

    return if (!hasOutputStream) {
        "suspend fun $operationName($inputParam)$outputParam"
    } else {
        val outputName = output.get()
        val inputSignature = if (inputParam.isNotEmpty()) "$inputParam, " else ""
        "suspend fun <T> $operationName(${inputSignature}block: suspend ($outputName) -> T): T"
    }
}
