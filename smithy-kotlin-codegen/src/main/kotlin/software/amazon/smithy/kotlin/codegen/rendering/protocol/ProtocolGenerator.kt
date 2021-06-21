/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.codegen.core.Symbol
import software.amazon.smithy.codegen.core.SymbolProvider
import software.amazon.smithy.kotlin.codegen.KotlinSettings
import software.amazon.smithy.kotlin.codegen.core.*
import software.amazon.smithy.kotlin.codegen.integration.KotlinIntegration
import software.amazon.smithy.kotlin.codegen.model.buildSymbol
import software.amazon.smithy.kotlin.codegen.model.namespace
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.Shape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.utils.CaseUtils

/**
 * Smithy protocol code generator(s)
 */
interface ProtocolGenerator {
    companion object {
        /**
         * Sanitizes the name of the protocol so it can be used as a symbol in Kotlin.
         *
         * For example, the default implementation converts '.' to '_' and converts '-'
         * to become camelCase separated words. `aws.rest-json-1.1` becomes `Aws_RestJson1_1`
         *
         * @param name Name of the protocol to sanitize
         * @return sanitized protocol name
         */
        fun getSanitizedName(name: String): String {
            val s1 = name.replace("(\\s|\\.|-)".toRegex(), "_")
            return CaseUtils.toCamelCase(s1, true, '_')
        }

        val DefaultServiceExceptionSymbol: Symbol = buildSymbol {
            name = "ServiceException"
            namespace(KotlinDependency.CORE)
        }
    }

    /**
     * Get the supported protocol [ShapeId]
     * e.g. `software.amazon.smithy.aws.traits.protocols.RestJson1Trait.ID`
     */
    val protocol: ShapeId

    /**
     * Get the name of the protocol
     */
    val protocolName: String
        get() {
            var prefix = protocol.namespace
            val idx = prefix.indexOf('.')
            if (idx != -1) {
                prefix = prefix.substring(0, idx)
            }
            return CaseUtils.toCamelCase(prefix) + getSanitizedName(protocol.name)
        }

    /**
     * Get the application protocol for the generator
     */
    val applicationProtocol: ApplicationProtocol

    /**
     * Get the symbol that should be used as the base class for generated service exceptions.
     * It defaults to the ServiceException available in smithy-kotlin's client-runtime.
     */
    val exceptionBaseClassSymbol: Symbol
        get() = DefaultServiceExceptionSymbol

    /**
     * Generate serializers required by the protocol
     */
    fun generateSerializers(ctx: GenerationContext)

    /**
     * Generate deserializers required by the protocol
     */
    fun generateDeserializers(ctx: GenerationContext)

    /**
     * Generate unit tests for the protocol
     */
    fun generateProtocolUnitTests(ctx: GenerationContext)

    /**
     * Generate an actual client implementation of the service interface
     */
    fun generateProtocolClient(ctx: GenerationContext)

    /**
     * Context object used for service serialization and deserialization
     */
    data class GenerationContext(
        val settings: KotlinSettings,
        val model: Model,
        val service: ServiceShape,
        val symbolProvider: SymbolProvider,
        val integrations: List<KotlinIntegration>,
        val protocol: ShapeId,
        val delegator: KotlinDelegator
    )
}

fun <T : Shape> ProtocolGenerator.GenerationContext.toRenderingContext(
    protocolGenerator: ProtocolGenerator,
    forShape: T? = null,
    writer: KotlinWriter,
): RenderingContext<T> =
    RenderingContext(writer, forShape, model, symbolProvider, settings, protocolGenerator, integrations)
