package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.MemberShape
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.shapes.ToShapeId
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.TimestampFormatTrait

/**
 * A generic subset of [HttpBinding] that is not specific to any protocol implementation.
 */
data class HttpBindingDescriptor(
    val member: MemberShape,
    val location: HttpBinding.Location,
    val locationName: String
) {
    companion object {
        fun fromHttpBinding(httpBinding: HttpBinding): HttpBindingDescriptor = HttpBindingDescriptor(httpBinding.member, httpBinding.location, httpBinding.locationName)
    }

    val memberName: String
        get() = member.memberName
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
    fun resolveBindingOperations(): List<OperationShape>

    /**
     * Return HttpTrait data from an operation suitable for the protocol implementation.
     * @param operationShape operation for which to retrieve the HttpTrait
     * @return [HttpTrait]

     */
    fun resolveHttpTrait(operationShape: OperationShape): HttpTrait

    /**
     * Return request bindings for an operation.
     * @param operationShape operation for which to retrieve bindings
     * @return all found http request bindings
     */
    fun resolveRequestBindings(operationShape: OperationShape): List<HttpBindingDescriptor>

    /**
     * Return response bindings for an operation.
     * @param shapeId shape for which to retrieve bindings
     * @return all found http response bindings
     */
    fun resolveResponseBindings(shapeId: ShapeId): List<HttpBindingDescriptor>

    /**
     * Determine the request content type for the protocol.
     *
     * @param operationShape operation associated with content type
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
    fun determineTimestampFormat(member: ToShapeId?, location: HttpBinding.Location?, defaultFormat: TimestampFormatTrait.Format?): TimestampFormatTrait.Format
}

/**
 * An Http Binding Resolver that relies on [HttpTrait] data from service models.
 */
class DefaultHttpBindingResolver(
    private val generationContext: ProtocolGenerator.GenerationContext,
    private val bindingIndex: HttpBindingIndex = HttpBindingIndex.of(generationContext.model),
    private val topDownIndex: TopDownIndex = TopDownIndex.of(generationContext.model)
) : HttpBindingResolver {
    /**
     * The default content-type when a document is synthesized in the body.
     */
    private val defaultContentType: String = "application/json"

    override fun resolveBindingOperations(): List<OperationShape> {
        return topDownIndex.getContainedOperations(generationContext.service)
            .filter { op -> op.hasTrait(HttpTrait::class.java) }.toList<OperationShape>()
    }

    override fun resolveHttpTrait(operationShape: OperationShape): HttpTrait = operationShape.expectTrait(HttpTrait::class.java)

    override fun resolveRequestBindings(operationShape: OperationShape): List<HttpBindingDescriptor> {
        return bindingIndex.getRequestBindings(operationShape).values.map { HttpBindingDescriptor.fromHttpBinding(it) }
    }

    override fun resolveResponseBindings(shapeId: ShapeId): List<HttpBindingDescriptor> {
        return bindingIndex.getResponseBindings(shapeId).values.map { HttpBindingDescriptor.fromHttpBinding(it) }
    }

    override fun determineRequestContentType(operationShape: OperationShape): String {
        return bindingIndex.determineRequestContentType(operationShape, defaultContentType).orElse(defaultContentType)
    }

    override fun determineTimestampFormat(
        member: ToShapeId?,
        location: HttpBinding.Location?,
        defaultFormat: TimestampFormatTrait.Format?
    ): TimestampFormatTrait.Format =
        bindingIndex.determineTimestampFormat(member, location, defaultFormat)
}
