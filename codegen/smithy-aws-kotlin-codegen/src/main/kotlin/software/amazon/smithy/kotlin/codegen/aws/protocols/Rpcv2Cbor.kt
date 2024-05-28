package software.amazon.smithy.kotlin.codegen.aws.protocols

import CborParserGenerator
import software.amazon.smithy.kotlin.codegen.aws.protocols.core.AwsHttpBindingProtocolGenerator
import software.amazon.smithy.kotlin.codegen.core.KotlinWriter
import software.amazon.smithy.kotlin.codegen.core.RuntimeTypes
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpBindingResolver
import software.amazon.smithy.kotlin.codegen.rendering.protocol.HttpTraitResolver
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolContentTypes
import software.amazon.smithy.kotlin.codegen.rendering.protocol.ProtocolGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.CborSerializerGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataParserGenerator
import software.amazon.smithy.kotlin.codegen.rendering.serde.StructuredDataSerializerGenerator
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.shapes.OperationShape
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.model.shapes.ShapeId
import software.amazon.smithy.model.traits.TimestampFormatTrait
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait

class Rpcv2Cbor : AwsHttpBindingProtocolGenerator() {
    override val protocol: ShapeId = Rpcv2CborTrait.ID
    override val defaultTimestampFormat = TimestampFormatTrait.Format.EPOCH_SECONDS

    override fun getProtocolHttpBindingResolver(model: Model, serviceShape: ServiceShape): HttpBindingResolver =
        HttpTraitResolver(
            model,
            serviceShape,
            ProtocolContentTypes("application/cbor", "application/cbor", "application/vnd.amazon.eventstream")
        )

    override fun structuredDataSerializer(ctx: ProtocolGenerator.GenerationContext): StructuredDataSerializerGenerator = CborSerializerGenerator(this)

    override fun structuredDataParser(ctx: ProtocolGenerator.GenerationContext): StructuredDataParserGenerator = CborParserGenerator()

    override fun renderDeserializeErrorDetails(
        ctx: ProtocolGenerator.GenerationContext,
        op: OperationShape,
        writer: KotlinWriter,
    ) {
        writer.write("#T.deserialize(payload)", RuntimeTypes.SmithyRpcv2Protocols.Cbor.Rpcv2CborErrorDeserializer)
    }
}