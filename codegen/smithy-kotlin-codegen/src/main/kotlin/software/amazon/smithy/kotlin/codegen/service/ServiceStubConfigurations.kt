package software.amazon.smithy.kotlin.codegen.service

import software.amazon.smithy.aws.traits.protocols.RestJson1Trait
import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.kotlin.codegen.core.GenerationContext
import software.amazon.smithy.kotlin.codegen.core.KotlinDelegator
import software.amazon.smithy.kotlin.codegen.model.getTrait
import software.amazon.smithy.model.shapes.ServiceShape
import software.amazon.smithy.protocol.traits.Rpcv2CborTrait

enum class ContentType(val value: String) {
    CBOR("CBOR"),
    JSON("JSON"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ContentType = when (value.uppercase()) {
            "CBOR" -> CBOR
            "JSON" -> JSON
            else -> throw IllegalArgumentException("$value is not a valid ContentType value, expected $JSON or $CBOR")
        }

        fun fromServiceShape(shape: ServiceShape): ContentType {
            if (shape.getTrait<Rpcv2CborTrait>() != null) {
                return CBOR
            } else if (shape.getTrait<RestJson1Trait>() != null) {
                return JSON
            }
            throw IllegalArgumentException("service shape does not a valid protocol")
        }
    }
}

enum class ServiceFramework(val value: String) {
    KTOR("ktor"),
    ;

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ServiceFramework = when (value.lowercase()) {
            "ktor" -> KTOR
            else -> throw IllegalArgumentException("$value is not a valid ServerFramework value, expected $KTOR")
        }
    }

    internal fun getServiceFrameworkGenerator(
        ctx: GenerationContext,
        delegator: KotlinDelegator,
        fileManifest: FileManifest,
    ): AbstractStubGenerator {
        when (this) {
            KTOR -> return KtorStubGenerator(ctx, delegator, fileManifest)
        }
    }
}
