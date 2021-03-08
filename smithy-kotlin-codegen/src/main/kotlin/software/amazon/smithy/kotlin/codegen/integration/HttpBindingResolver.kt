package software.amazon.smithy.kotlin.codegen.integration

import software.amazon.smithy.kotlin.codegen.*
import software.amazon.smithy.model.knowledge.HttpBinding
import software.amazon.smithy.model.knowledge.HttpBindingIndex
import software.amazon.smithy.model.knowledge.TopDownIndex
import software.amazon.smithy.model.shapes.*
import software.amazon.smithy.model.traits.HttpTrait
import software.amazon.smithy.model.traits.JsonNameTrait
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
 * Provides format-specific serde codegen.  Implementors of this
 * interface are able to specify field and object descriptor codegen output
 * for a specific message format, such as JSON or XML.
 */
interface SerdeMessageFormatHandler {
    /**
     * Add the necessary imports to the codegen file for message format specific types.
     */
    fun addSerdeImports(writer: KotlinWriter)

    /**
     * Return the format-specific trait that specifies the name of a field.
     * @param memberShape shape to generate a serial name for.
     * @param namePostfix an optional postfix to the name in the case of synthetic nested members.
     * @return the serial name of field suitable for writing directly to codegen output
     */
    fun serialNameTraitForMember(memberShape: MemberShape, namePostfix: String = ""): String

    /**
     * Return the format-specific trait that specifies the name of a struct (or object).
     * @param objShape shape to generate a serial name for.
     * @return the serial name of object suitable for writing directly to codegen output,
     *          or null if nothing should be emitted to codegen.
     */
    fun serialNameTraitForStruct(objShape: Shape): String?
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

/**
 * A [SerdeMessageFormatHandler] for protocols utilizing JSON message formats.
 */
class JsonMessageFormatHandler : SerdeMessageFormatHandler {
    override fun addSerdeImports(writer: KotlinWriter) {
        writer.addImport(KotlinDependency.CLIENT_RT_SERDE.namespace, "*")
        writer.addImport(KotlinDependency.CLIENT_RT_SERDE_JSON.namespace, "JsonSerialName")
        writer.dependencies.addAll(KotlinDependency.CLIENT_RT_SERDE.dependencies)
        writer.dependencies.addAll(KotlinDependency.CLIENT_RT_SERDE_JSON.dependencies)
    }

    override fun serialNameTraitForMember(memberShape: MemberShape, namePostfix: String): String {
        val serialName = memberShape.getTrait<JsonNameTrait>()?.value ?: memberShape.memberName
        return """JsonSerialName("$serialName$namePostfix")"""
    }

    // JSON message format does not use a name for an object
    override fun serialNameTraitForStruct(objShape: Shape): String? = null
}
