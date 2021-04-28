/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */

package software.amazon.smithy.kotlin.codegen.rendering.protocol

import software.amazon.smithy.kotlin.codegen.model.expectTrait
import software.amazon.smithy.kotlin.codegen.model.hasTrait
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * A generic subset of [HttpBinding] that is not specific to any protocol implementation.
 */
data class HttpBindingDescriptor(
    val member: MemberShape,
    val location: HttpBinding.Location,
    val locationName: String? = null
) {
    /**
     * @param Smithy [HttpBinding] to create from
     */
    constructor(httpBinding: HttpBinding) : this(httpBinding.member, httpBinding.location, httpBinding.locationName)

    val memberName: String = member.memberName
}

/**
 * Represents a protocol-specific implementation that resolves http binding data.
 * Smithy protocol implementations need to supply a protocol-specific implementation
 * of this type in order to use [HttpBindingProtocolGenerator].
 */
interface HttpBindingResolver {
    /**
     * Return from the model all operations with HTTP bindings (explicit/implicit/or assumed)
     */
    fun bindingOperations(): List<OperationShape>

    /**
     * Return HttpTrait data from an operation suitable for the protocol implementation.
     * @param operationShape [OperationShape] for which to retrieve the HttpTrait
     * @return [HttpTrait]

     */
    fun httpTrait(operationShape: OperationShape): HttpTrait

    /**
     * Return request bindings for an operation.
     * @param operationShape [OperationShape] for which to retrieve bindings
     * @return all found http request bindings
     */
    fun requestBindings(operationShape: OperationShape): List<HttpBindingDescriptor>

    /**
     * Return response bindings for an operation.
     * @param shape [Shape] for which to retrieve bindings
     * @return all found http response bindings
     */
    fun responseBindings(shape: Shape): List<HttpBindingDescriptor>

    /**
     * Determine the request content type for the protocol.
     *
     * @param operationShape [OperationShape] associated with content type
     * @return content type
     */
    fun determineRequestContentType(operationShape: OperationShape): String

    /**
     * Determine the timestamp format depending on input parameter values.
     *
     * @param member id of member
     * @param location location in the request for timestamp format
     * @param defaultFormat default value
     */
    fun determineTimestampFormat(
        member: ToShapeId,
        location: HttpBinding.Location,
        defaultFormat: TimestampFormatTrait.Format
    ): TimestampFormatTrait.Format
}

/**
 * An Http Binding Resolver that relies on [HttpTrait] data from service models.
 * @param generationContext [ProtocolGenerator.GenerationContext] from which model state is retrieved
 * @param
 */
class HttpTraitResolver(
    private val generationContext: ProtocolGenerator.GenerationContext,
    private val defaultContentType: String,
    private val bindingIndex: HttpBindingIndex = HttpBindingIndex.of(generationContext.model),
    private val topDownIndex: TopDownIndex = TopDownIndex.of(generationContext.model)
) : HttpBindingResolver {

    override fun bindingOperations(): List<OperationShape> {
        return topDownIndex.getContainedOperations(generationContext.service)
            .filter { op -> op.hasTrait<HttpTrait>() }.toList<OperationShape>()
    }

    override fun httpTrait(operationShape: OperationShape): HttpTrait = operationShape.expectTrait()

    override fun requestBindings(operationShape: OperationShape): List<HttpBindingDescriptor> {
        return bindingIndex.getRequestBindings(operationShape).values.map { HttpBindingDescriptor(it) }
    }

    override fun responseBindings(shape: Shape): List<HttpBindingDescriptor> {
        return when (shape) {
            is OperationShape,
            is StructureShape -> bindingIndex.getResponseBindings(shape.toShapeId()).values.map { HttpBindingDescriptor(it) }
            else -> error { "Unimplemented resolving bindings for ${shape.javaClass.canonicalName}" }
        }
    }

    override fun determineRequestContentType(operationShape: OperationShape): String {
        return bindingIndex.determineRequestContentType(operationShape, defaultContentType).orElse(defaultContentType)
    }

    override fun determineTimestampFormat(
        member: ToShapeId,
        location: HttpBinding.Location,
        defaultFormat: TimestampFormatTrait.Format
    ): TimestampFormatTrait.Format =
        bindingIndex.determineTimestampFormat(member, location, defaultFormat)
}
